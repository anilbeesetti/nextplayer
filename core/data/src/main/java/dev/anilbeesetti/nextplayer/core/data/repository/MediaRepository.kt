package dev.anilbeesetti.nextplayer.core.data.repository

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
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

    /**
     * Get list of [Video]s as flow for a specific directory
     * @param directoryPath the path the directory from which the videos should be returned
     * @return flow of list of video items
     */
    fun getVideosFlowFromDirectory(directoryPath: String): Flow<List<Video>>

    /**
     * Get list of [Directory]s as flow
     * @return flow of list of directory items
     */
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
    suspend fun getVideoState(path: String): VideoState?

    suspend fun deleteVideos(videoUris: List<String>, intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>)

    suspend fun deleteFolders(folderPaths: List<String>, intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>)
}
