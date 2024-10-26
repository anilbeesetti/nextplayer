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
    fun saveMediumUiState(
        uri: String,
        externalSubs: List<Uri>,
        videoScale: Float,
    )

    suspend fun getVideoState(uri: String): VideoState?
    suspend fun addExternalSubtitle(mediaUri: String, subtitleUri: Uri)
    suspend fun externalSubtitlesFlowForVideo(uri: String): Flow<List<Uri>>

    fun updateMediumPosition(uri: String, position: Long)
    fun updateMediumPlaybackSpeed(uri: String, playbackSpeed: Float)
    fun updateMediumAudioTrack(uri: String, audioTrackIndex: Int)
    fun updateMediumSubtitleTrack(uri: String, subtitleTrackIndex: Int)
}
