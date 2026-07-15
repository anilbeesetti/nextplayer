package dev.anilbeesetti.nextplayer.core.data.playlist

import dev.anilbeesetti.nextplayer.core.model.PlaylistItemInput
import javax.inject.Inject

data class M3uParseResult(
    val entries: List<PlaylistItemInput>,
    val skippedEntries: Int,
)

class M3uParser @Inject constructor() {
    fun parse(
        content: String,
        resolveEntry: (String) -> String?,
    ): M3uParseResult {
        val entries = mutableListOf<PlaylistItemInput>()
        val seenUris = mutableSetOf<String>()
        var nextTitle: String? = null
        var skippedEntries = 0
        var parsedEntries = 0

        content.removePrefix("\uFEFF").lineSequence().forEach { line ->
            val value = line.trim()
            when {
                value.isEmpty() -> Unit
                value.startsWith("#EXTINF", ignoreCase = true) -> {
                    nextTitle = value.substringAfter(',', missingDelimiterValue = "")
                        .trim()
                        .ifEmpty { null }
                }
                value.startsWith('#') -> Unit
                else -> {
                    parsedEntries++
                    if (parsedEntries > PlaylistLimits.MAX_ENTRIES) {
                        throw PlaylistEntryLimitExceededException(PlaylistLimits.MAX_ENTRIES)
                    }
                    val resolvedUri = resolveEntry(value)
                    if (resolvedUri == null) {
                        skippedEntries++
                    } else if (seenUris.add(resolvedUri)) {
                        entries += PlaylistItemInput(uriString = resolvedUri, title = nextTitle)
                    }
                    nextTitle = null
                }
            }
        }

        return M3uParseResult(entries = entries, skippedEntries = skippedEntries)
    }
}
