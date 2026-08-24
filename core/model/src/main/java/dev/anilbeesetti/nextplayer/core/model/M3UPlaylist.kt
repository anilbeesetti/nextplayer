package dev.anilbeesetti.nextplayer.core.model

data class M3UPlaylist(
    val playlistName: String,
    val items: List<M3UPlaylistItem>,
)

data class M3UPlaylistItem(
    val uri: String,
    val title: String?,
    val tvgLogo: String?,
    val duration: Int = UNKNOWN_DURATION,
    val groupTitle: String?,
) {
    companion object {
        const val UNKNOWN_DURATION = -1
    }
}
