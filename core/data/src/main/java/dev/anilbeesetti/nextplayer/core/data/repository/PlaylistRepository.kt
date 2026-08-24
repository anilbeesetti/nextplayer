package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.model.M3UPlaylist
import dev.anilbeesetti.nextplayer.core.model.M3UPlaylistItem
import dev.anilbeesetti.nextplayer.core.model.PlaylistRecord
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {

    fun observePlaylists(): Flow<List<PlaylistSummary>>

    fun observePlaylist(playlistId: Long): Flow<PlaylistRecord?>

    suspend fun getPlaylist(playlistId: Long): PlaylistRecord?

    suspend fun create(name: String, videoUris: List<String> = emptyList()): Long

    suspend fun createM3U(
        type: PlaylistType,
        source: String,
        playlist: M3UPlaylist,
    ): Long

    suspend fun replaceM3UItems(playlistId: Long, items: List<M3UPlaylistItem>)

    suspend fun rename(playlistId: Long, name: String)

    suspend fun delete(playlistId: Long)

    suspend fun addVideos(playlistId: Long, videoUris: List<String>): Int

    suspend fun removeVideo(playlistId: Long, videoUri: String)

    suspend fun replaceOrder(playlistId: Long, orderedUris: List<String>)

    suspend fun markVideoPlayed(playlistId: Long, videoUri: String)

    suspend fun removeMissingVideos(existingUris: Set<String>)

    suspend fun countFilePlaylistsBySource(source: String): Int
}
