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

    override suspend fun getPosition(path: String): Long? {
        return videoDao.get(path)?.playbackPosition
    }

    override suspend fun updatePosition(path: String, position: Long) {
        videoDao.upsert(
            VideoEntity(
                path = path,
                playbackPosition = position
            )
        )
    }
}
