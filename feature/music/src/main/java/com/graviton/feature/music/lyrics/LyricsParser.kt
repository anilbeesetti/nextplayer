package com.graviton.feature.music.lyrics

import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** A timed word; [endMs] may equal [startMs] when the source only supplies a start. */
data class LyricWord(val text: String, val startMs: Long, val endMs: Long)

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val endMs: Long? = null,
    val words: List<LyricWord> = emptyList(),
    val translation: String? = null,
    val voice: String? = null,
    val isBackground: Boolean = false,
)

data class LyricsDocument(
    val lines: List<LyricLine>,
    val unsynced: String?,
    val offsetMs: Long = 0L,
    val source: String? = null,
    val instrumental: Boolean = false,
) {
    val isSynced: Boolean get() = lines.isNotEmpty()
    val hasWordTiming: Boolean get() = lines.any { it.words.isNotEmpty() }

    fun lineAt(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        return lines.binarySearchBy(positionMs) { it.timeMs }.let { index ->
            if (index >= 0) index else (-index - 2).coerceAtLeast(0)
        }
    }
}

/** Clean-room LRC/TTML parser. Invalid input is returned as unsynchronised text, never thrown. */
object LyricsParser {
    private val lrcTimestamp = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val enhancedTimestamp = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")
    private val offsetTag = Regex("""^\s*\[offset:([+-]?\d+)]\s*$""", RegexOption.IGNORE_CASE)
    private val metadataTag = Regex("""^\s*\[[a-zA-Z]+:.*]\s*$""")

    fun parse(raw: String?): LyricsDocument {
        if (raw.isNullOrBlank()) return LyricsDocument(emptyList(), null)
        return if (raw.trimStart().startsWith("<") && raw.contains("<tt", ignoreCase = true)) {
            parseTtml(raw)
        } else {
            parseLrc(raw)
        }
    }

    fun parseLrc(raw: String): LyricsDocument {
        var offset = 0L
        val parsed = mutableListOf<LyricLine>()
        val unsynced = mutableListOf<String>()
        raw.lineSequence().forEach { sourceLine ->
            offsetTag.matchEntire(sourceLine)?.let {
                offset = it.groupValues[1].toLongOrNull() ?: 0L
                return@forEach
            }
            val stamps = lrcTimestamp.findAll(sourceLine).toList()
            if (stamps.isEmpty()) {
                if (!metadataTag.matches(sourceLine) && sourceLine.isNotEmpty()) unsynced += sourceLine
                return@forEach
            }
            val content = lrcTimestamp.replace(sourceLine, "").trim()
            val words = parseEnhancedWords(content)
            val plainText = if (words.isEmpty()) content else words.joinToString("") { it.text }.trim()
            // Blank timed lines are significant: they preserve instrumental pauses and scrolling.
            stamps.forEach { stamp ->
                val lineStart = stamp.toMillis() + offset
                val adjustedWords = words.map { word ->
                    word.copy(startMs = word.startMs + offset, endMs = word.endMs + offset)
                }
                parsed += LyricLine(lineStart.coerceAtLeast(0), plainText, words = adjustedWords)
            }
        }
        val sorted = parsed.sortedBy { it.timeMs }
        // Consecutive duplicate timestamps are conventionally original + translation.
        val merged = mutableListOf<LyricLine>()
        sorted.forEach { line ->
            val previous = merged.lastOrNull()
            if (previous != null && previous.timeMs == line.timeMs && previous.text != line.text && previous.translation == null) {
                merged[merged.lastIndex] = previous.copy(translation = line.text)
            } else {
                merged += line
            }
        }
        val bounded = merged.mapIndexed { index, line ->
            line.copy(endMs = line.endMs ?: merged.getOrNull(index + 1)?.timeMs)
        }
        return LyricsDocument(bounded, unsynced.joinToString("\n").takeIf(String::isNotBlank), offset)
    }

    fun parseTtml(raw: String): LyricsDocument = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "") }
            runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "") }
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(raw)))
        val nodes = document.getElementsByTagNameNS("*", "p")
        val lines = buildList {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                val start = parseTtmlTime(element.attributeAny("begin")) ?: continue
                val end = parseTtmlTime(element.attributeAny("end"))
                    ?: parseTtmlTime(element.attributeAny("dur"))?.let(start::plus)
                val spans = element.getElementsByTagNameNS("*", "span")
                val words = buildList {
                    for (spanIndex in 0 until spans.length) {
                        val span = spans.item(spanIndex) as? Element ?: continue
                        val wordStart = parseTtmlTime(span.attributeAny("begin")) ?: continue
                        val wordEnd = parseTtmlTime(span.attributeAny("end")) ?: wordStart
                        val text = span.textContent.orEmpty()
                        if (text.isNotEmpty()) add(LyricWord(text, wordStart, wordEnd))
                    }
                }
                val role = element.attributeAny("role")
                val voice = element.attributeAny("agent").ifBlank { element.attributeAny("voice") }.ifBlank { null }
                val text = if (words.isNotEmpty()) words.joinToString("") { it.text }.trim() else element.textContent.orEmpty().trim()
                val translation = element.childElements().firstOrNull {
                    it.attributeAny("role").contains("translation", true) || it.attributeAny("type").contains("translation", true)
                }?.textContent?.trim()
                add(
                    LyricLine(
                        timeMs = start,
                        text = text,
                        endMs = end,
                        words = words,
                        translation = translation,
                        voice = voice,
                        isBackground = role.contains("background", true),
                    ),
                )
            }
        }.sortedBy { it.timeMs }
        LyricsDocument(lines, null)
    }.getOrElse { LyricsDocument(emptyList(), raw.takeIf(String::isNotBlank)) }

    private fun parseEnhancedWords(content: String): List<LyricWord> {
        val matches = enhancedTimestamp.findAll(content).toList()
        if (matches.isEmpty()) return emptyList()
        return matches.mapIndexedNotNull { index, match ->
            val start = match.toMillis()
            val textStart = match.range.last + 1
            val textEnd = matches.getOrNull(index + 1)?.range?.first ?: content.length
            val text = content.substring(textStart, textEnd)
            if (text.isEmpty()) null else LyricWord(text, start, matches.getOrNull(index + 1)?.toMillis() ?: start)
        }
    }

    private fun MatchResult.toMillis(): Long {
        val fraction = groupValues[3]
        val milliseconds = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100L
            2 -> fraction.toLong() * 10L
            else -> fraction.take(3).toLong()
        }
        return groupValues[1].toLong() * 60_000L + groupValues[2].toLong() * 1_000L + milliseconds
    }

    private fun parseTtmlTime(value: String): Long? {
        if (value.isBlank()) return null
        if (value.endsWith("ms")) return value.dropLast(2).toDoubleOrNull()?.toLong()
        if (value.endsWith("s")) return value.dropLast(1).toDoubleOrNull()?.times(1000)?.toLong()
        val parts = value.split(':')
        if (parts.size !in 2..3) return null
        val seconds = parts.last().toDoubleOrNull() ?: return null
        val minutes = parts[parts.lastIndex - 1].toLongOrNull() ?: return null
        val hours = parts.getOrNull(parts.size - 3)?.toLongOrNull() ?: 0L
        return (hours * 3_600_000L + minutes * 60_000L + seconds * 1000).toLong()
    }

    private fun Element.attributeAny(localName: String): String {
        if (hasAttribute(localName)) return getAttribute(localName)
        for (index in 0 until attributes.length) {
            val item = attributes.item(index)
            if (item.localName == localName || item.nodeName.substringAfter(':') == localName) return item.nodeValue.orEmpty()
        }
        return ""
    }

    private fun Element.childElements(): List<Element> = buildList {
        for (index in 0 until childNodes.length) (childNodes.item(index) as? Element)?.let(::add)
    }
}
