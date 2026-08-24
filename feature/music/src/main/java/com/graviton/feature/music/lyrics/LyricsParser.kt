package com.graviton.feature.music.lyrics

data class LyricLine(
    val timeMs: Long,
    val text: String,
)

data class LyricsDocument(
    val lines: List<LyricLine>,
    val unsynced: String?,
) {
    val isSynced: Boolean get() = lines.isNotEmpty()

    fun lineAt(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        val index = lines.indexOfLast { it.timeMs <= positionMs }
        return index.coerceAtLeast(0)
    }
}

object LyricsParser {
    private val timestamp = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

    fun parse(raw: String?): LyricsDocument {
        if (raw.isNullOrBlank()) return LyricsDocument(emptyList(), null)
        val synced = mutableListOf<LyricLine>()
        val unsynced = StringBuilder()
        raw.lineSequence().forEach { line ->
            val matches = timestamp.findAll(line).toList()
            if (matches.isEmpty()) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("[")) {
                    if (unsynced.isNotEmpty()) unsynced.append('\n')
                    unsynced.append(trimmed)
                }
                return@forEach
            }
            val text = timestamp.replace(line, "").trim()
            if (text.isEmpty()) return@forEach
            matches.forEach { match ->
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val fraction = match.groupValues[3]
                val millis = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100L
                    2 -> fraction.toLong() * 10L
                    else -> fraction.take(3).toLong()
                }
                synced += LyricLine(timeMs = minutes * 60_000 + seconds * 1_000 + millis, text = text)
            }
        }
        return LyricsDocument(
            lines = synced.sortedBy { it.timeMs },
            unsynced = unsynced.toString().takeIf { it.isNotBlank() },
        )
    }
}
