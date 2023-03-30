package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.data.mappers.toVideo
import dev.anilbeesetti.nextplayer.core.data.models.Video
import dev.anilbeesetti.nextplayer.core.database.dao.VideoDao
import dev.anilbeesetti.nextplayer.core.database.entities.VideoEntity
import dev.anilbeesetti.nextplayer.core.media.mediasource.MediaSource
import dev.anilbeesetti.nextplayer.core.media.model.MediaVideo
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

class LocalVideoRepository @Inject constructor(
    private val videoDao: VideoDao,
    private val mediaSource: MediaSource
) : VideoRepository {
    override fun getVideosFlow(): Flow<List<Video>> {
        return mediaSource.getVideoItemsFlow()
            .map { mediaList ->
                mediaList.map(MediaVideo::toVideo)
            }
    }

    override suspend fun getVideoState(path: String): VideoState? {
        return videoDao.get(path)?.toVideoState()
    }

    override suspend fun saveVideoState(
        path: String,
        position: Long,
        audioTrackIndex: Int?,
        subtitleTrackIndex: Int?
    ) {
        Timber.d("save state for [$path]: [$position, $audioTrackIndex, $subtitleTrackIndex]")
        videoDao.upsert(
            VideoEntity(
                path = path,
                playbackPosition = position,
                audioTrack = audioTrackIndex,
                subtitleTrack = subtitleTrackIndex
            )
        )
    }
}

data class VideoState(
    val path: String,
    val position: Long,
    val audioTrack: Int?,
    val subtitleTrack: Int?
)

fun VideoEntity.toVideoState(): VideoState {
    return VideoState(
        path = path,
        position = playbackPosition,
        audioTrack = audioTrack,
        subtitleTrack = subtitleTrack
    )
}
