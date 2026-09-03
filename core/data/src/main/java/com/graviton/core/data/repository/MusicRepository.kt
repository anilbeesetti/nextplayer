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

    /**
     * Appends [trackIds] to the MediaStore playlist [playlistId].
     *
     * Returns `false` when the platform rejects the write. MediaStore playlists are deprecated from
     * Android 10 and read-only on many Android 11+ builds, so callers must surface the failure
     * rather than assume success.
     */
    suspend fun addTracksToPlaylist(playlistId: Long, trackIds: List<Long>): Boolean

    /** Creates a MediaStore playlist and returns its id, or `null` when the platform refuses. */
    suspend fun createPlaylist(name: String): Long?

    fun refresh()
}
