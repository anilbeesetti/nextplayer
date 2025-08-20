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
import dev.anilbeesetti.nextplayer.core.database.dao.MediumStateDao
import dev.anilbeesetti.nextplayer.core.database.entities.MediumStateEntity
import dev.anilbeesetti.nextplayer.core.database.relations.DirectoryWithMedia
import dev.anilbeesetti.nextplayer.core.database.relations.MediumWithInfo
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.Video
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class LocalMediaRepository @Inject constructor(
    private val mediumDao: MediumDao,
    private val mediumStateDao: MediumStateDao,
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

    override suspend fun getVideoByUri(uri: String): Video? {
        return mediumDao.getWithInfo(uri)?.toVideo()
    }

    override suspend fun getVideoState(uri: String): VideoState? {
        return mediumStateDao.get(uri)?.toVideoState()
    }

    override fun updateMediumLastPlayedTime(uri: String, lastPlayedTime: Long) {
        applicationScope.launch {
            val stateEntity = mediumStateDao.get(uri) ?: MediumStateEntity(uriString = uri)

            mediumStateDao.upsert(
                mediumState = stateEntity.copy(
                    lastPlayedTime = lastPlayedTime,
                ),
            )
        }
    }

    override fun updateMediumPosition(uri: String, position: Long) {
        applicationScope.launch {
            val duration = mediumDao.get(uri)?.duration ?: position.plus(1)
            val adjustedPosition = when {
                position < 0 -> 0L
                position >= duration -> return@launch
                else -> position
            }

            val stateEntity = mediumStateDao.get(uri) ?: MediumStateEntity(uriString = uri)

            mediumStateDao.upsert(
                mediumState = stateEntity.copy(
                    playbackPosition = adjustedPosition,
                    lastPlayedTime = System.currentTimeMillis(),
                ),
            )
        }
    }

    override fun updateMediumPlaybackSpeed(uri: String, playbackSpeed: Float) {
        applicationScope.launch {
            val stateEntity = mediumStateDao.get(uri) ?: MediumStateEntity(uriString = uri)

            mediumStateDao.upsert(
                mediumState = stateEntity.copy(
                    playbackSpeed = playbackSpeed,
                    lastPlayedTime = System.currentTimeMillis(),
                ),
            )
        }
    }

    override fun updateMediumAudioTrack(uri: String, audioTrackIndex: Int) {
        applicationScope.launch {
            val stateEntity = mediumStateDao.get(uri) ?: MediumStateEntity(uriString = uri)

            mediumStateDao.upsert(
                mediumState = stateEntity.copy(
                    audioTrackIndex = audioTrackIndex,
                    lastPlayedTime = System.currentTimeMillis(),
                ),
            )
        }
    }

    override fun updateMediumSubtitleTrack(uri: String, subtitleTrackIndex: Int) {
        applicationScope.launch {
            val stateEntity = mediumStateDao.get(uri) ?: MediumStateEntity(uriString = uri)

            mediumStateDao.upsert(
                mediumState = stateEntity.copy(
                    subtitleTrackIndex = subtitleTrackIndex,
                    lastPlayedTime = System.currentTimeMillis(),
                ),
            )
        }
    }

    override fun updateMediumZoom(uri: String, zoom: Float) {
        applicationScope.launch {
            val stateEntity = mediumStateDao.get(uri) ?: MediumStateEntity(uriString = uri)

            mediumStateDao.upsert(
                mediumState = stateEntity.copy(
                    videoScale = zoom,
                    lastPlayedTime = System.currentTimeMillis(),
                ),
            )
        }
    }

    override fun addExternalSubtitleToMedium(uri: String, subtitleUri: Uri) {
        applicationScope.launch {
            val stateEntity = mediumStateDao.get(uri) ?: MediumStateEntity(uriString = uri)
            val currentExternalSubs = UriListConverter.fromStringToList(stateEntity.externalSubs)

            if (currentExternalSubs.contains(subtitleUri)) return@launch
            val newExternalSubs = UriListConverter.fromListToString(urlList = currentExternalSubs + subtitleUri)

            mediumStateDao.upsert(
                mediumState = stateEntity.copy(
                    externalSubs = newExternalSubs,
                    lastPlayedTime = System.currentTimeMillis(),
                ),
            )
        }
    }
}
