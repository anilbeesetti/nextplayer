package dev.anilbeesetti.nextplayer.core.data.repository

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.common.di.ApplicationScope
import dev.anilbeesetti.nextplayer.core.data.mappers.toFolder
import dev.anilbeesetti.nextplayer.core.data.mappers.toVideo
import dev.anilbeesetti.nextplayer.core.data.mappers.toVideoState
import dev.anilbeesetti.nextplayer.core.data.models.VideoState
import dev.anilbeesetti.nextplayer.core.database.converter.UriListConverter
import dev.anilbeesetti.nextplayer.core.database.dao.DirectoryDao
import dev.anilbeesetti.nextplayer.core.database.dao.MediumDao
import dev.anilbeesetti.nextplayer.core.database.relations.DirectoryWithMedia
import dev.anilbeesetti.nextplayer.core.database.relations.MediumWithInfo
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.Video
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

class LocalMediaRepository @Inject constructor(
    private val mediumDao: MediumDao,
    private val directoryDao: DirectoryDao,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : MediaRepository {

    override fun getVideosFlow(): Flow<List<Video>> {
        return mediumDao.getAllWithInfo().map { it.map(MediumWithInfo::toVideo) }
    }

    override fun getVideosFlowFromFolderPath(folderPath: String): Flow<List<Video>> {
        return mediumDao.getAllWithInfoFromDirectory(folderPath).map { it.map(MediumWithInfo::toVideo) }
    }

    override fun getFoldersFlow(): Flow<List<Folder>> {
        return directoryDao.getAllWithMedia().map { it.map(DirectoryWithMedia::toFolder) }
    }

    override suspend fun getVideoState(uri: String): VideoState? {
        return mediumDao.get(uri)?.toVideoState()
    }

    override suspend fun saveVideoState(
        uri: String,
        position: Long,
        audioTrackIndex: Int?,
        subtitleTrackIndex: Int?,
        playbackSpeed: Float?,
        externalSubs: List<Uri>,
    ) {
        Timber.d(
            "save state for [$uri]: [$position, $audioTrackIndex, $subtitleTrackIndex, $playbackSpeed]",
        )

        applicationScope.launch {
            mediumDao.updateMediumState(
                uri = uri,
                position = position,
                audioTrackIndex = audioTrackIndex,
                subtitleTrackIndex = subtitleTrackIndex,
                playbackSpeed = playbackSpeed,
                externalSubs = UriListConverter.fromListToString(externalSubs),
                lastPlayedTime = System.currentTimeMillis(),
            )
        }
    }
}
