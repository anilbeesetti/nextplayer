package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.common.di.ApplicationScope
import dev.anilbeesetti.nextplayer.core.data.mappers.toDirectory
import dev.anilbeesetti.nextplayer.core.data.mappers.toVideo
import dev.anilbeesetti.nextplayer.core.data.mappers.toVideoState
import dev.anilbeesetti.nextplayer.core.data.models.VideoState
import dev.anilbeesetti.nextplayer.core.database.dao.DirectoryDao
import dev.anilbeesetti.nextplayer.core.database.dao.MediumDao
import dev.anilbeesetti.nextplayer.core.database.entities.DirectoryEntity
import dev.anilbeesetti.nextplayer.core.database.entities.MediumEntity
import dev.anilbeesetti.nextplayer.core.media.mediasource.MediaSource
import dev.anilbeesetti.nextplayer.core.media.model.MediaVideo
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.Video
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

class LocalVideoRepository @Inject constructor(
    private val mediumDao: MediumDao,
    private val directoryDao: DirectoryDao,
    @ApplicationScope private val applicationScope: CoroutineScope
) : VideoRepository {

    override fun getVideosFlow(): Flow<List<Video>> {
        return mediumDao.getAll().map { it.map(MediumEntity::toVideo) }
    }

    override fun getDirectoriesFlow(): Flow<List<Folder>> {
        return directoryDao.getAll().map { it.map(DirectoryEntity::toDirectory) }
    }

    override suspend fun getVideoState(path: String): VideoState? {
        return mediumDao.get(path)?.toVideoState()
    }

    override suspend fun saveVideoState(
        path: String,
        position: Long,
        audioTrackIndex: Int?,
        subtitleTrackIndex: Int?,
        playbackSpeed: Float
    ) {
        Timber.d("save state for [$path]: [$position, $audioTrackIndex, $subtitleTrackIndex]")

        applicationScope.launch {
            mediumDao.updateMediumState(
                path = path,
                position = position,
                audioTrackIndex = audioTrackIndex,
                subtitleTrackIndex = subtitleTrackIndex,
                playbackSpeed = playbackSpeed
            )
        }
    }
}
