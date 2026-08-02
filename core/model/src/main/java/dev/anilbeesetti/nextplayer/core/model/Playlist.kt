package dev.anilbeesetti.nextplayer.core.model

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val itemCount: Int,
)

data class PlaylistRecord(
    val id: Long,
    val name: String,
    val orderedUris: List<String>,
)

data class PlaylistItem(
    val position: Int,
    val video: Video,
)

data class Playlist(
    val id: Long,
    val name: String,
    val items: List<PlaylistItem>,
)
