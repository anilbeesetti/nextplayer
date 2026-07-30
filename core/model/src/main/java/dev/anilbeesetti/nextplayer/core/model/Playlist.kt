package dev.anilbeesetti.nextplayer.core.model

enum class PlaylistType { EDITABLE, M3U_URL, M3U_FILE }

data class PlaylistItemInput(
    val uriString: String,
    val title: String? = null,
    val imageUrl: String? = null,
    val displayPath: String? = null,
)

data class PlaylistItem(
    val uriString: String,
    val title: String?,
    val position: Int,
    val imageUrl: String? = null,
    val displayPath: String? = null,
)

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val type: PlaylistType,
    val itemCount: Int,
    val lastRefreshedAt: Long?,
)

data class Playlist(
    val id: Long,
    val name: String,
    val type: PlaylistType,
    val source: String?,
    val items: List<PlaylistItem>,
    val lastRefreshedAt: Long?,
)
