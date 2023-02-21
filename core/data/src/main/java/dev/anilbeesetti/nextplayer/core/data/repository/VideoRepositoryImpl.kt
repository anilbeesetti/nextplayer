package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.data.models.PlayerItem
import dev.anilbeesetti.nextplayer.core.data.models.VideoItem
import dev.anilbeesetti.nextplayer.core.data.medialibrary.MediaLibrary
import dev.anilbeesetti.nextplayer.core.database.dao.VideoDao
import dev.anilbeesetti.nextplayer.core.database.entities.VideoEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class VideoRepositoryImpl @Inject constructor(
    private val videoDao: VideoDao,
    private val mediaLibrary: MediaLibrary
) : VideoRepository {
    override fun getVideoItemsFlow(): Flow<List<VideoItem>> = mediaLibrary.getVideoItemsFlow()

    override fun getLocalPlayerItems(): List<PlayerItem> {
        return mediaLibrary.getVideoItems().map { it.toPlayerItem() }
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

fun VideoItem.toPlayerItem(): PlayerItem {
    return PlayerItem(
        path = path,
        duration = duration,
    )
}
