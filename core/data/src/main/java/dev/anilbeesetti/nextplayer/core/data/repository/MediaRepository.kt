package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.data.models.VideoState
import dev.anilbeesetti.nextplayer.core.model.Directory
import dev.anilbeesetti.nextplayer.core.model.Video
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    /**
     * Get list of [Video]s as flow
     * @return flow of list of video items
     */
    fun getVideosFlow(): Flow<List<Video>>

    fun getDirectoriesFlow(): Flow<List<Directory>>

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
        subtitleTrackIndex: Int?,
        playbackSpeed: Float?
    )

    /**
     * Get video state
     * @param path path of the video
     * @return [VideoState] of the video
     */
    suspend fun getVideoState(path: String): VideoState?
}
