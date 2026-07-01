package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.model.MediaInfo
import dev.anilbeesetti.nextplayer.core.model.Video
import kotlinx.coroutines.flow.Flow

interface VaultRepository {

    fun observeHiddenVideos(): Flow<List<Video>>

    /** Moves [videos] into the vault and records them as hidden. */
    suspend fun hideVideos(videos: List<Video>)

    /** Moves the given vault [videos] back to their original locations. */
    suspend fun unhideVideos(videos: List<Video>): UnhideResult

    /** Permanently deletes the given vault [videos] (both the file and the record). */
    suspend fun deleteHiddenVideos(videos: List<Video>)

    suspend fun getHiddenVideoInfo(id: Long): MediaInfo?
}

/**
 * Outcome of an unhide operation.
 *
 * @param relocatedCount number of videos that couldn't be restored to their original folder
 *   (e.g. a custom folder or storage root, which scoped storage disallows) and were instead
 *   placed in the fallback directory.
 */
data class UnhideResult(
    val relocatedCount: Int = 0,
)
