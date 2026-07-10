package dev.anilbeesetti.nextplayer.core.model

import java.io.Serializable

/**
 * A single IPTV channel parsed from an `#EXTINF` entry of an M3U/M3U8 playlist.
 *
 * @param url the stream url (usually an `.m3u8` HLS manifest, but may be any scheme ExoPlayer supports).
 * @param name display name of the channel (taken from the `#EXTINF` title).
 * @param logoUrl optional channel logo (`tvg-logo`).
 * @param groupTitle optional category the channel belongs to (`group-title`), e.g. "News", "Sports".
 * @param tvgId optional EPG id (`tvg-id`).
 * @param isLive whether the stream is a live broadcast. Live streams are flagged with a red [LIVE]
 *   badge in the UI. Defaults to true because IPTV playlists are overwhelmingly live channels.
 */
data class IptvChannel(
    val id: Long = 0,
    val playlistId: Long = 0,
    val name: String,
    val url: String,
    val logoUrl: String? = null,
    val groupTitle: String? = null,
    val tvgId: String? = null,
    val isLive: Boolean = true,
) : Serializable {
    companion object {
        val sample = IptvChannel(
            id = 1,
            playlistId = 1,
            name = "Sample News HD",
            url = "https://example.com/live/news.m3u8",
            logoUrl = null,
            groupTitle = "News",
            isLive = true,
        )
    }
}
