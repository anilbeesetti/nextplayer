package com.graviton.core.model

const val MUSIC_HISTORY_LIMIT = 100

fun ApplicationPreferences.recordMusicPlay(
    uri: String,
    folderPath: String? = null,
    countPlay: Boolean = false,
    playedAt: Long = System.currentTimeMillis(),
): ApplicationPreferences {
    if (uri.isBlank()) return this
    val recent = (listOf(uri) + musicRecentlyPlayedUris.filter { it != uri }).take(MUSIC_HISTORY_LIMIT)
    val folders = if (folderPath.isNullOrBlank()) {
        musicFolderLastUri
    } else {
        musicFolderLastUri + (folderPath to uri)
    }
    return copy(
        musicRecentlyPlayedUris = recent,
        musicFolderLastUri = folders,
        musicPlayCounts = if (countPlay) musicPlayCounts + (uri to ((musicPlayCounts[uri] ?: 0) + 1)) else musicPlayCounts,
        musicLastPlayedAt = if (countPlay) musicLastPlayedAt + (uri to playedAt) else musicLastPlayedAt,
    )
}

fun ApplicationPreferences.lastMusicUriForFolder(folderPath: String): String? =
    musicFolderLastUri[folderPath]

fun startIndexForFolderPlayback(
    trackUris: List<String>,
    lastPlayedUri: String?,
): Int {
    if (trackUris.isEmpty()) return 0
    val index = lastPlayedUri?.let { trackUris.indexOf(it) } ?: -1
    return if (index >= 0) index else 0
}
