package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.data.models.PlayerItem
import dev.anilbeesetti.nextplayer.core.data.models.Video
import dev.anilbeesetti.nextplayer.core.database.dao.VideoDao
import dev.anilbeesetti.nextplayer.core.database.entities.VideoEntity
import dev.anilbeesetti.nextplayer.core.media.mediasource.MediaSource
import dev.anilbeesetti.nextplayer.core.media.model.MediaVideo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject

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

    override fun getLocalPlayerItems(): List<PlayerItem> {
        return mediaSource.getVideoItems().map(MediaVideo::toPlayerItem)
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

fun MediaVideo.toPlayerItem(): PlayerItem {
    return PlayerItem(
        path = data,
        duration = duration
    )
}

fun MediaVideo.toVideo(): Video {
    val videoFile = File(data)

    return Video(
        id = id,
        path = data,
        duration = duration,
        uriString = uri.toString(),
        displayName = videoFile.nameWithoutExtension,
        nameWithExtension = videoFile.name,
        width = width,
        height = height
    )
}
