package dev.anilbeesetti.nextplayer.core.data.repository.fake

import dev.anilbeesetti.nextplayer.core.data.mappers.toDirectory
import dev.anilbeesetti.nextplayer.core.data.mappers.toVideo
import dev.anilbeesetti.nextplayer.core.data.mappers.toVideoState
import dev.anilbeesetti.nextplayer.core.data.models.VideoState
import dev.anilbeesetti.nextplayer.core.data.repository.VideoRepository
import dev.anilbeesetti.nextplayer.core.database.entities.DirectoryEntity
import dev.anilbeesetti.nextplayer.core.database.entities.MediumEntity
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.Video
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeVideoRepository : VideoRepository {

    private val videoEntities = mutableListOf<MediumEntity>()
    private val directoryEntities = mutableListOf<DirectoryEntity>()
    override fun getVideosFlow(): Flow<List<Video>> {
        return flowOf(videoEntities.map(MediumEntity::toVideo))
    }

    override fun getDirectoriesFlow(): Flow<List<Folder>> {
        return flowOf(directoryEntities.map(DirectoryEntity::toDirectory))
    }

    override suspend fun saveVideoState(
        path: String,
        position: Long,
        audioTrackIndex: Int?,
        subtitleTrackIndex: Int?,
        playbackSpeed: Float
    ) {
        videoEntities.find { it.path == path }?.let {
            videoEntities.remove(it)
            videoEntities.add(
                it.copy(
                    playbackPosition = position,
                    audioTrackIndex = audioTrackIndex,
                    subtitleTrackIndex = subtitleTrackIndex
                )
            )
        }
    }

    override suspend fun getVideoState(path: String): VideoState? {
        return videoEntities.find { it.path == path }?.toVideoState()
    }
}
