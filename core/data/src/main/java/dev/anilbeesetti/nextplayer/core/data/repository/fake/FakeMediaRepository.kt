package dev.anilbeesetti.nextplayer.core.data.repository.fake

import dev.anilbeesetti.nextplayer.core.data.models.VideoState
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.model.Directory
import dev.anilbeesetti.nextplayer.core.model.Video
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeMediaRepository : MediaRepository {

    val videos = mutableListOf<Video>()
    val directories = mutableListOf<Directory>()

    override fun getVideosFlow(): Flow<List<Video>> {
        return flowOf(videos)
    }

    override fun getDirectoriesFlow(): Flow<List<Directory>> {
        return flowOf(directories)
    }

    override suspend fun saveVideoState(
        path: String,
        position: Long,
        audioTrackIndex: Int?,
        subtitleTrackIndex: Int?,
        playbackSpeed: Float?
    ) {
    }

    override suspend fun saveVideoState(videoState: VideoState) {
    }

    override suspend fun getVideoState(path: String): VideoState? {
        return null
    }
}
