package dev.anilbeesetti.nextplayer.feature.player.utils

internal fun resolvePlaylistStartIndex(
    playlist: List<String>,
    originalSelectedUri: String,
    normalizedSelectedUri: String?,
): Int {
    val originalIndex = playlist.indexOf(originalSelectedUri)
    if (originalIndex >= 0) return originalIndex

    val normalizedIndex = normalizedSelectedUri?.let(playlist::indexOf) ?: -1
    return normalizedIndex.takeIf { it >= 0 } ?: 0
}
