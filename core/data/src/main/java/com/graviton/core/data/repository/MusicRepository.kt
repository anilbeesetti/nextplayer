package com.graviton.core.data.repository

import com.graviton.core.model.AudioTrack
import com.graviton.core.model.MusicPlaylist
import kotlinx.coroutines.flow.Flow

/**
 * MediaStore-backed music library. Music is not copied into a second database: MediaStore remains
 * the source of truth, just like it is for the video library.
 */
interface MusicRepository {
    fun observeTracks(): Flow<List<AudioTrack>>
    fun observePlaylists(): Flow<List<MusicPlaylist>>
    suspend fun getPlaylistTrackIds(playlistId: Long): List<Long>
    suspend fun getTrack(uriString: String): AudioTrack?
    fun refresh()
}
