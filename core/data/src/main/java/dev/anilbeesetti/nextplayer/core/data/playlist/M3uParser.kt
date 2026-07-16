package dev.anilbeesetti.nextplayer.core.data.playlist

import dev.anilbeesetti.nextplayer.core.model.PlaylistItemInput
import java.util.Locale
import javax.inject.Inject

data class M3uParseResult(
    val entries: List<PlaylistItemInput>,
    val skippedEntries: Int,
)

private data class ExtInfMetadata(
    val title: String?,
    val imageReference: String?,
)

class M3uParser @Inject constructor() {
    fun parse(
        content: String,
        resolveEntry: (String) -> String?,
    ): M3uParseResult {
        val entries = mutableListOf<PlaylistItemInput>()
        val seenUris = mutableSetOf<String>()
        var pending = ExtInfMetadata(title = null, imageReference = null)
        var skippedEntries = 0

        content.removePrefix("\uFEFF").lineSequence().forEach { line ->
            val value = line.trim()
            when {
                value.isEmpty() -> Unit
                value.startsWith("#EXTINF", ignoreCase = true) -> {
                    pending = value.extInfMetadata()
                }
                value.startsWith("#EXTIMG:", ignoreCase = true) -> {
                    if (pending.imageReference == null) {
                        pending = pending.copy(
                            imageReference = value.substringAfter(':').trim().ifBlank { null },
                        )
                    }
                }
                value.startsWith('#') -> Unit
                else -> {
                    val resolvedUri = resolveEntry(value)
                    if (resolvedUri == null) {
                        skippedEntries++
                    } else if (seenUris.add(resolvedUri)) {
                        if (entries.size >= PlaylistLimits.MAX_ENTRIES) {
                            throw PlaylistEntryLimitExceededException(PlaylistLimits.MAX_ENTRIES)
                        }
                        entries += PlaylistItemInput(
                            uriString = resolvedUri,
                            title = pending.title,
                            imageUrl = pending.imageReference?.let(resolveEntry),
                        )
                    }
                    pending = ExtInfMetadata(title = null, imageReference = null)
                }
            }
        }

        return M3uParseResult(entries = entries, skippedEntries = skippedEntries)
    }
}

private val extInfAttribute = Regex(
    """([A-Za-z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,]+))""",
)

private fun String.extInfMetadata(): ExtInfMetadata {
    val commaIndex = firstUnquotedComma()
    val attributeSection = if (commaIndex >= 0) substring(0, commaIndex) else this
    val attributes = attributeSection.extInfAttributes()
    val commaTitle = if (commaIndex >= 0) substring(commaIndex + 1).trim() else ""
    val title = commaTitle.ifBlank { attributes["tvg-name"].orEmpty() }.ifBlank { null }
    val imageReference = attributes["tvg-logo"]
        ?.takeIf(String::isNotBlank)
        ?: attributes["logo"]?.takeIf(String::isNotBlank)
    return ExtInfMetadata(title = title, imageReference = imageReference)
}

private fun String.extInfAttributes(): Map<String, String> =
    extInfAttribute.findAll(this).associate { match ->
        val value = match.groupValues.drop(2).firstOrNull(String::isNotEmpty).orEmpty()
        match.groupValues[1].lowercase(Locale.ROOT) to value
    }

private fun String.firstUnquotedComma(): Int {
    var quote: Char? = null
    forEachIndexed { index, char ->
        when {
            quote == null && (char == '\'' || char == '"') -> quote = char
            quote == char -> quote = null
            quote == null && char == ',' -> return index
        }
    }
    return -1
}
