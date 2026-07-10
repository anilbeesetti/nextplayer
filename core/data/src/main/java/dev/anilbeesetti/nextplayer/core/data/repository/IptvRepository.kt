package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.model.IptvChannel
import dev.anilbeesetti.nextplayer.core.model.IptvPlaylist
import kotlinx.coroutines.flow.Flow

interface IptvRepository {

    /** All imported playlists, most-recently-added first, each carrying its channel count. */
    fun observePlaylists(): Flow<List<IptvPlaylist>>

    /** All channels across every playlist. */
    fun observeAllChannels(): Flow<List<IptvChannel>>

    /** Channels belonging to a single [playlistId]. */
    fun observeChannels(playlistId: Long): Flow<List<IptvChannel>>

    /**
     * Downloads the M3U/M3U8 playlist at [url], parses it and stores its channels. If a playlist
     * with the same [url] already exists its channels are replaced (a refresh).
     */
    suspend fun importFromUrl(url: String, name: String? = null): ImportResult

    /**
     * Parses already-read M3U [content] (e.g. from a user-picked file) and stores it under [name].
     * [source] uniquely identifies the file so re-importing the same file refreshes it.
     */
    suspend fun importFromContent(content: String, name: String, source: String): ImportResult

    /** Re-downloads a URL-backed playlist. No-op for file-backed playlists. */
    suspend fun refreshPlaylist(playlist: IptvPlaylist): ImportResult

    suspend fun deletePlaylist(playlistId: Long)
}

sealed interface ImportResult {
    data class Success(val playlistId: Long, val channelCount: Int) : ImportResult
    data class Error(val message: String) : ImportResult
}
