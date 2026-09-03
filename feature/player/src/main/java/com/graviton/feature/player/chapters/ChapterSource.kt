package com.graviton.feature.player.chapters

import android.content.Context
import android.net.Uri
import com.graviton.core.common.extensions.getPath
import com.graviton.core.model.MediaChapter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads chapter descriptions that ship next to a media file.
 *
 * Two sidecar formats are understood, both of which are what desktop players write out:
 *  - OGM style `CHAPTER01=00:00:00.000` / `CHAPTER01NAME=Intro` pairs;
 *  - FFmetadata `[CHAPTER]` blocks with `TIMEBASE`, `START` and `title`.
 *
 * Nothing is inferred. A file with no readable sidecar returns an empty list so the chapter UI can
 * show a real empty state instead of fabricated chapters.
 */
object ChapterSource {

    private val SIDECAR_SUFFIXES = listOf(
        ".chapters.txt",
        ".chapters",
        ".chapters.ffmeta",
        ".ffmeta",
    )

    suspend fun chaptersFor(context: Context, mediaUri: Uri): List<MediaChapter> = withContext(Dispatchers.IO) {
        val path = runCatching { context.getPath(mediaUri) }.getOrNull() ?: return@withContext emptyList()
        val mediaFile = File(path)
        if (!mediaFile.isFile) return@withContext emptyList()

        val baseName = mediaFile.nameWithoutExtension
        val parent = mediaFile.parentFile ?: return@withContext emptyList()

        val sidecar = SIDECAR_SUFFIXES
            .asSequence()
            .map { File(parent, baseName + it) }
            .firstOrNull { it.isFile && it.length() <= MAX_SIDECAR_BYTES }
            ?: return@withContext emptyList()

        val text = runCatching { sidecar.readText() }.getOrNull() ?: return@withContext emptyList()
        parse(text)
    }

    /** Parses either supported sidecar syntax. Exposed for unit testing. */
    fun parse(text: String): List<MediaChapter> {
        val ogm = parseOgm(text)
        if (ogm.isNotEmpty()) return ogm
        return parseFfMetadata(text)
    }

    private fun parseOgm(text: String): List<MediaChapter> {
        val starts = HashMap<String, Long>()
        val names = HashMap<String, String>()

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            val separator = line.indexOf('=')
            if (separator <= 0) return@forEach
            val key = line.substring(0, separator).uppercase()
            val value = line.substring(separator + 1).trim()
            when {
                key.startsWith("CHAPTER") && key.endsWith("NAME") ->
                    names[key.removePrefix("CHAPTER").removeSuffix("NAME")] = value
                key.startsWith("CHAPTER") ->
                    parseTimestamp(value)?.let { starts[key.removePrefix("CHAPTER")] = it }
            }
        }

        return starts.entries
            .sortedBy { it.key }
            .map { (id, startMs) -> MediaChapter(startMs = startMs, title = names[id].orEmpty()) }
    }

    private fun parseFfMetadata(text: String): List<MediaChapter> {
        val chapters = mutableListOf<MediaChapter>()
        var inChapter = false
        var timebaseDenominator = 1_000L
        var start: Long? = null
        var title = ""

        fun flush() {
            val startValue = start ?: return
            val startMs = if (timebaseDenominator <= 0L) {
                startValue
            } else {
                startValue * 1_000L / timebaseDenominator
            }
            chapters += MediaChapter(startMs = startMs, title = title)
            start = null
            title = ""
        }

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.equals("[CHAPTER]", ignoreCase = true) -> {
                    if (inChapter) flush()
                    inChapter = true
                    timebaseDenominator = 1_000L
                }
                !inChapter -> Unit
                line.startsWith("[") -> {
                    flush()
                    inChapter = false
                }
                else -> {
                    val separator = line.indexOf('=')
                    if (separator <= 0) return@forEach
                    val key = line.substring(0, separator).trim().uppercase()
                    val value = line.substring(separator + 1).trim()
                    when (key) {
                        "TIMEBASE" -> value.substringAfter('/', "").toLongOrNull()?.let { timebaseDenominator = it }
                        "START" -> start = value.toLongOrNull()
                        "TITLE" -> title = value
                    }
                }
            }
        }
        if (inChapter) flush()

        return chapters.sortedBy { it.startMs }
    }

    /** Parses `HH:MM:SS.mmm`, `MM:SS.mmm` or `SS.mmm` into milliseconds. */
    private fun parseTimestamp(value: String): Long? {
        val parts = value.split(':')
        if (parts.isEmpty() || parts.size > 3) return null

        // Everything before the last component is an hour/minute unit; the last one carries the
        // fractional seconds. Folding the leading units in base 60 handles SS, MM:SS and HH:MM:SS
        // with the same arithmetic.
        var leadingUnits = 0L
        parts.dropLast(1).forEach { part ->
            val unit = part.trim().toLongOrNull() ?: return null
            leadingUnits = leadingUnits * 60L + unit
        }
        val seconds = parts.last().trim().toDoubleOrNull() ?: return null
        return leadingUnits * 60_000L + (seconds * 1_000).toLong()
    }

    private const val MAX_SIDECAR_BYTES = 1L * 1024 * 1024
}
