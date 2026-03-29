package dev.anilbeesetti.nextplayer.core.data.repository

import android.net.Uri
import android.os.Environment
import dev.anilbeesetti.nextplayer.core.data.models.VideoState
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.MediaInfo
import dev.anilbeesetti.nextplayer.core.model.Video
import kotlinx.coroutines.flow.Flow

/**
 * Repository for accessing media (videos and folders) and managing video playback state.
 *
 * All methods that take a `folderPath` parameter will return media recursively
 * from that path and all its subdirectories.
 */
interface MediaRepository {

    /**
     * Observes all unique folders containing videos under the given path.
     *
     * @param folderPath The root path to search for videos. Defaults to external storage root.
     * @return A flow of folder lists.
     */
    fun observeFolders(
        folderPath: String = Environment.getExternalStorageDirectory().path,
    ): Flow<List<Folder>>

    /**
     * Observes all videos under the given path recursively.
     *
     * @param folderPath The root path to search for videos. Defaults to external storage root.
     * @return A flow of video lists.
     */
    fun observeVideos(
        folderPath: String = Environment.getExternalStorageDirectory().path,
    ): Flow<List<Video>>

    /**
     * Fetches all unique folders containing videos under the given path (one-shot).
     *
     * @param folderPath The root path to search for videos. Defaults to external storage root.
     * @return List of folders.
     */
    suspend fun fetchFolders(
        folderPath: String = Environment.getExternalStorageDirectory().path,
    ): List<Folder>

    /**
     * Fetches all videos under the given path recursively (one-shot).
     *
     * @param folderPath The root path to search for videos. Defaults to external storage root.
     * @return List of videos.
     */
    suspend fun fetchVideos(
        folderPath: String = Environment.getExternalStorageDirectory().path,
    ): List<Video>

    suspend fun getVideoByUri(uri: String): Video?
    suspend fun getVideoState(uri: String): VideoState?
    suspend fun getMediaInfo(uri: String): MediaInfo?
    suspend fun updateMediumLastPlayedTime(uri: String, lastPlayedTime: Long)
    suspend fun updateMediumPosition(uri: String, position: Long)
    suspend fun updateMediumPlaybackSpeed(uri: String, playbackSpeed: Float)
    suspend fun updateMediumAudioTrack(uri: String, audioTrackIndex: Int)
    suspend fun updateMediumSubtitleTrack(uri: String, subtitleTrackIndex: Int)
    suspend fun updateMediumZoom(uri: String, zoom: Float)
    suspend fun addExternalSubtitleToMedium(uri: String, subtitleUri: Uri)
    suspend fun updateSubtitleDelay(uri: String, delay: Long)
    suspend fun updateSubtitleSpeed(uri: String, speed: Float)
}
