package dev.anilbeesetti.nextplayer.core.data.repository

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.data.models.VideoState
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.Video
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun getVideosFlow(): Flow<List<Video>>
    fun getVideosFlowFromFolderPath(folderPath: String): Flow<List<Video>>
    fun getFoldersFlow(): Flow<List<Folder>>

    suspend fun getVideoState(uri: String): VideoState?

    fun updateMediumPosition(uri: String, position: Long)
    fun updateMediumPlaybackSpeed(uri: String, playbackSpeed: Float)
    fun updateMediumAudioTrack(uri: String, audioTrackIndex: Int)
    fun updateMediumSubtitleTrack(uri: String, subtitleTrackIndex: Int)
    fun updateMediumZoom(uri: String, zoom: Float)

    fun addExternalSubtitleToMedium(uri: String, subtitleUri: Uri)
}
