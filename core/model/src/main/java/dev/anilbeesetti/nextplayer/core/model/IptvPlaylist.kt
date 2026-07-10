package dev.anilbeesetti.nextplayer.core.model

import java.io.Serializable

/**
 * A source of IPTV channels imported from an M3U/M3U8 playlist, either from a remote URL
 * or a local file the user uploaded.
 */
data class IptvPlaylist(
    val id: Long = 0,
    val name: String,
    val source: String,
    val sourceType: IptvSourceType,
    val channelCount: Int = 0,
    val addedAt: Long = 0,
) : Serializable

enum class IptvSourceType {
    /** Remote playlist that can be refreshed from its [IptvPlaylist.source] URL. */
    URL,

    /** Playlist imported from a local file. Its contents are stored at import time. */
    FILE,
}
