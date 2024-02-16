package dev.anilbeesetti.nextplayer.core.data.repository

import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import dev.anilbeesetti.nextplayer.core.data.models.VideoState
import dev.anilbeesetti.nextplayer.core.model.Folder
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
     * @param folderPath the path the directory from which the videos should be returned
     * @return flow of list of video items
     */
    fun getVideosFlowFromFolderPath(folderPath: String): Flow<List<Video>>

    /**
     * Get list of [Folder]s as flow
     * @return flow of list of folder items
     */
    fun getFoldersFlow(): Flow<List<Folder>>

    /**
     * Save video state
     * @param uri uri of the video
     * @param position position in milliseconds
     * @param audioTrackIndex index of the audio track
     * @param subtitleTrackIndex index of the subtitle track
     * -1 to disable track
     * null to not change track
     */
    suspend fun saveVideoState(uri: String, position: Long, audioTrackIndex: Int?, subtitleTrackIndex: Int?, playbackSpeed: Float?, externalSubs: List<Uri>)

    suspend fun getVideoState(uri: String): VideoState?

    suspend fun deleteVideos(videoUris: List<String>, intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>)

    suspend fun deleteFolders(folderPaths: List<String>, intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>)
}
