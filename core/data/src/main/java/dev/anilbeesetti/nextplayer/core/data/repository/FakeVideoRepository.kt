package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.data.mappers.toVideoState
import dev.anilbeesetti.nextplayer.core.data.models.Folder
import dev.anilbeesetti.nextplayer.core.data.models.Video
import dev.anilbeesetti.nextplayer.core.data.models.VideoState
import dev.anilbeesetti.nextplayer.core.database.entities.VideoEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeVideoRepository : VideoRepository {

    val videoItems = mutableListOf<Video>()
    val folders = mutableListOf<Folder>()
    private val videoEntities = mutableListOf<VideoEntity>()
    override fun getVideosFlow(folderPath: String?): Flow<List<Video>> {
        return flowOf(videoItems)
    }

    override fun getFoldersFlow(): Flow<List<Folder>> {
        return flowOf(folders)
    }

    override suspend fun saveVideoState(
        path: String,
        position: Long,
        audioTrackIndex: Int?,
        subtitleTrackIndex: Int?
    ) {
        videoEntities.find { it.path == path }?.let {
            videoEntities.remove(it)
            videoEntities.add(
                it.copy(
                    playbackPosition = position,
                    audioTrack = audioTrackIndex,
                    subtitleTrack = subtitleTrackIndex
                )
            )
        }
    }

    override suspend fun getVideoState(path: String): VideoState? {
        return videoEntities.find { it.path == path }?.toVideoState()
    }
}
