package com.graviton.core.data.repository

import com.graviton.core.model.PlaylistRecord
import com.graviton.core.model.PlaylistSummary
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {

    fun observePlaylists(): Flow<List<PlaylistSummary>>

    fun observePlaylist(playlistId: Long): Flow<PlaylistRecord?>

    suspend fun create(name: String, videoUris: List<String> = emptyList()): Long

    suspend fun rename(playlistId: Long, name: String)

    suspend fun delete(playlistId: Long)

    suspend fun addVideos(playlistId: Long, videoUris: List<String>): Int

    suspend fun removeVideo(playlistId: Long, videoUri: String)

    suspend fun replaceOrder(playlistId: Long, orderedUris: List<String>)

    suspend fun markVideoPlayed(playlistId: Long, videoUri: String)

}
