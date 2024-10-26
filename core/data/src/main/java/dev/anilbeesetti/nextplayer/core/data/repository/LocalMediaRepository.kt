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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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

    override suspend fun addExternalSubtitle(mediaUri: String, subtitleUri: Uri) {
        val currentExternalSubs = externalSubtitlesFlowForVideo(mediaUri).first().filterNot { it == subtitleUri }
        mediumDao.addExternalSubtitle(
            mediumUri = mediaUri,
            externalSubs = UriListConverter.fromListToString(currentExternalSubs + subtitleUri),
        )
    }

    override suspend fun externalSubtitlesFlowForVideo(uri: String): Flow<List<Uri>> {
        return mediumDao.getAsFlow(uri).map { mediumEntity ->
            mediumEntity?.let { UriListConverter.fromStringToList(it.externalSubs) } ?: emptyList()
        }.distinctUntilChanged()
    }

    override fun saveMediumUiState(
        uri: String,
        externalSubs: List<Uri>,
        videoScale: Float,
    ) {
        applicationScope.launch {
            mediumDao.updateMediumUiState(
                uri = uri,
                externalSubs = UriListConverter.fromListToString(externalSubs),
                videoScale = videoScale,
            )
        }
    }

    override fun saveMediumState(
        uri: String,
        position: Long,
        audioTrackIndex: Int?,
        subtitleTrackIndex: Int?,
        playbackSpeed: Float?,
    ) {
        applicationScope.launch {
            mediumDao.updateMediumState(
                uri = uri,
                position = position,
                audioTrackIndex = audioTrackIndex,
                subtitleTrackIndex = subtitleTrackIndex,
                playbackSpeed = playbackSpeed,
                lastPlayedTime = System.currentTimeMillis(),
            )
        }
    }

    override fun updateMediumPosition(uri: String, position: Long) {
        applicationScope.launch {
            mediumDao.updateMediumPosition(uri, position)
            mediumDao.updateMediumLastPlayedTime(uri, System.currentTimeMillis())
        }
    }

    override fun updateMediumPlaybackSpeed(uri: String, playbackSpeed: Float) {
        applicationScope.launch {
            mediumDao.updateMediumPlaybackSpeed(uri, playbackSpeed)
            mediumDao.updateMediumLastPlayedTime(uri, System.currentTimeMillis())
        }
    }

    override fun updateMediumAudioTrack(uri: String, audioTrackIndex: Int) {
        applicationScope.launch {
            mediumDao.updateMediumAudioTrack(uri, audioTrackIndex)
            mediumDao.updateMediumLastPlayedTime(uri, System.currentTimeMillis())
        }
    }

    override fun updateMediumSubtitleTrack(uri: String, subtitleTrackIndex: Int) {
        applicationScope.launch {
            mediumDao.updateMediumSubtitleTrack(uri, subtitleTrackIndex)
            mediumDao.updateMediumLastPlayedTime(uri, System.currentTimeMillis())
        }
    }
}
