package dev.anilbeesetti.nextplayer.core.model

enum class PlaylistType {
    LOCAL,
    M3U_URL,
    M3U_FILE,
}

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val type: PlaylistType,
    val itemCount: Int,
    val lastRefreshedAt: Long?,
)

data class PlaylistRecord(
    val id: Long,
    val name: String,
    val type: PlaylistType,
    val source: String?,
    val items: List<PlaylistItemRecord>,
    val lastRefreshedAt: Long?,
)

data class PlaylistItemRecord(
    val position: Int,
    val uri: String,
    val title: String? = null,
    val tvgLogo: String? = null,
    val duration: Int = M3UPlaylistItem.UNKNOWN_DURATION,
    val groupTitle: String? = null,
    val lastPlayedAt: Long? = null,
)

data class PlaylistItem(
    val position: Int,
    val uri: String,
    val title: String?,
    val tvgLogo: String?,
    val duration: Int,
    val groupTitle: String?,
    val video: Video?,
    val lastPlayedAt: Long? = null,
) {
    val displayTitle: String
        get() = title?.takeIf(String::isNotBlank)
            ?: video?.displayName
            ?: uri.substringBefore('?').substringAfterLast('/').substringBeforeLast('.')
                .ifBlank { uri }

    val supportingText: String
        get() = video?.parentPath?.takeIf(String::isNotBlank) ?: uri
}

data class Playlist(
    val id: Long,
    val name: String,
    val type: PlaylistType,
    val source: String?,
    val items: List<PlaylistItem>,
    val lastRefreshedAt: Long?,
) {
    val lastPlayedItem: PlaylistItem?
        get() = items
            .maxByOrNull { it.lastPlayedAt ?: Long.MIN_VALUE }
            ?.takeIf { it.lastPlayedAt != null }
}
