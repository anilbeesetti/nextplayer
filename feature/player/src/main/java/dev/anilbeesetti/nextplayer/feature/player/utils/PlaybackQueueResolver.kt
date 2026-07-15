package dev.anilbeesetti.nextplayer.feature.player.utils

internal suspend fun resolvePlaybackQueue(
    selectedUri: String,
    normalizedSelectedUri: String?,
    explicitPlaylist: List<String>,
    getLocalPlaylist: suspend (String) -> List<String>,
): List<String> = explicitPlaylist.takeIf { it.isNotEmpty() }
    ?: normalizedSelectedUri?.let { mediaUri ->
        getLocalPlaylist(mediaUri)
            .toMutableList()
            .apply {
                if (!contains(mediaUri)) {
                    add(index = 0, element = mediaUri)
                }
            }
    } ?: listOf(selectedUri)
