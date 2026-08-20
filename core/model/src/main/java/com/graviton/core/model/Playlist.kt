package com.graviton.core.model

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val itemCount: Int,
)

data class PlaylistRecord(
    val id: Long,
    val name: String,
    val items: List<PlaylistItemRecord>,
)

data class PlaylistItemRecord(
    val position: Int,
    val uri: String,
    val lastPlayedAt: Long? = null,
)

data class PlaylistItem(
    val position: Int,
    val video: Video,
    val lastPlayedAt: Long? = null,
)

data class Playlist(
    val id: Long,
    val name: String,
    val items: List<PlaylistItem>,
) {
    val lastPlayedVideo: Video?
        get() = items
            .maxByOrNull { it.lastPlayedAt ?: Long.MIN_VALUE }
            ?.takeIf { it.lastPlayedAt != null }
            ?.video
}
