package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.data.models.Video
import dev.anilbeesetti.nextplayer.core.database.entities.VideoEntity
import kotlinx.coroutines.flow.Flow

interface VideoRepository {

    /**
     * Get list of [Video]s as flow
     * @return flow of list of video items
     */
    fun getVideosFlow(): Flow<List<Video>>

    /**
     * Get position from path
     * @param path path of the video
     * @return position in milliseconds
     */
    suspend fun getPosition(path: String): Long?

    /**
     * Update position of the video
     * @param path path of the video
     * @param position position in milliseconds
     */
    suspend fun updatePosition(path: String, position: Long)

    /**
     * Save video state
     * @param path path of the video
     * @param position position in milliseconds
     * @param audioTrackIndex index of the audio track
     * @param subtitleTrackIndex index of the subtitle track
     * -1 to disable track
     * null to not change track
     */
    suspend fun saveVideoState(
        path: String,
        position: Long,
        audioTrackIndex: Int?,
        subtitleTrackIndex: Int?
    )

    suspend fun getVideoState(path: String): VideoState?
}
