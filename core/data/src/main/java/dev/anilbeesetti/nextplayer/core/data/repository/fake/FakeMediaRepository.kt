package dev.anilbeesetti.nextplayer.core.data.repository.fake

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.data.models.VideoState
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.Video
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeMediaRepository : MediaRepository {

    val videos = mutableListOf<Video>()
    val directories = mutableListOf<Folder>()

    override fun getVideosFlow(): Flow<List<Video>> {
        return flowOf(videos)
    }

    override fun getVideosFlowFromFolderPath(folderPath: String): Flow<List<Video>> {
        return flowOf(videos)
    }

    override fun getFoldersFlow(): Flow<List<Folder>> {
        return flowOf(directories)
    }

    override fun saveMediumUiState(
        uri: String,
        externalSubs: List<Uri>,
        videoScale: Float,
    ) {
    }

    override fun saveMediumState(
        uri: String,
        position: Long,
        audioTrackIndex: Int?,
        subtitleTrackIndex: Int?,
        playbackSpeed: Float?,
    ) {
    }

    override suspend fun getVideoState(uri: String): VideoState? {
        return null
    }

    override suspend fun addExternalSubtitle(mediaUri: String, subtitleUri: Uri) {
    }

    override suspend fun externalSubtitlesFlowForVideo(uri: String): Flow<List<Uri>> {
        return flowOf(emptyList())
    }

    override fun updateMediumPosition(uri: String, position: Long) {
    }

    override fun updateMediumPlaybackSpeed(uri: String, playbackSpeed: Float) {
    }

    override fun updateMediumAudioTrack(uri: String, audioTrackIndex: Int) {
    }

    override fun updateMediumSubtitleTrack(uri: String, subtitleTrackIndex: Int) {
    }
}
