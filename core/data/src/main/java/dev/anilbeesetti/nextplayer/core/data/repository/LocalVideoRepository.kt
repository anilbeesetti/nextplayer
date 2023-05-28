package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.data.mappers.toVideo
import dev.anilbeesetti.nextplayer.core.data.mappers.toVideoState
import dev.anilbeesetti.nextplayer.core.database.dao.VideoDao
import dev.anilbeesetti.nextplayer.core.database.entities.VideoEntity
import dev.anilbeesetti.nextplayer.core.media.mediasource.MediaSource
import dev.anilbeesetti.nextplayer.core.media.model.MediaVideo
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.data.models.VideoState
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class LocalVideoRepository @Inject constructor(
    private val videoDao: VideoDao,
    private val mediaSource: MediaSource
) : VideoRepository {

    override fun getVideosFlow(): Flow<List<Video>> {
        return mediaSource.getMediaVideosFlow().map { it.map(MediaVideo::toVideo) }
    }

    override suspend fun getVideoState(path: String): VideoState? {
        return videoDao.get(path)?.toVideoState()
    }

    override suspend fun saveVideoState(
        path: String,
        position: Long,
        audioTrackIndex: Int?,
        subtitleTrackIndex: Int?,
        playbackSpeed: Float
    ) {
        Timber.d("save state for [$path]: [$position, $audioTrackIndex, $subtitleTrackIndex]")

        // TODO: Replace global scope with application wide scope
        GlobalScope.launch {
            videoDao.upsert(
                VideoEntity(
                    path = path,
                    playbackPosition = position,
                    audioTrack = audioTrackIndex,
                    subtitleTrack = subtitleTrackIndex,
                    playbackSpeed = playbackSpeed
                )
            )
        }
    }
}
