package dev.anilbeesetti.nextplayer.core.data.repository

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.data.models.VideoState
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.FolderFilter
import dev.anilbeesetti.nextplayer.core.model.Video
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun observeFolders(filter: FolderFilter): Flow<List<Folder>>
    fun observeVideos(filter: FolderFilter): Flow<List<Video>>

    suspend fun fetchFolders(filter: FolderFilter): List<Folder>
    suspend fun fetchVideos(filter: FolderFilter): List<Video>

    suspend fun getVideoByUri(uri: String): Video?
    suspend fun getVideoState(uri: String): VideoState?

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
