package dev.anilbeesetti.nextplayer.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.data.models.PlayerItem
import dev.anilbeesetti.nextplayer.core.data.models.VideoItem
import dev.anilbeesetti.nextplayer.core.data.util.queryVideoItems
import dev.anilbeesetti.nextplayer.core.data.util.queryVideoItemsAsFlow
import dev.anilbeesetti.nextplayer.core.database.dao.VideoDao
import dev.anilbeesetti.nextplayer.core.database.entities.VideoEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class VideoRepositoryImpl @Inject constructor(
    private val videoDao: VideoDao,
    @ApplicationContext private val context: Context
) : VideoRepository {
    override fun getVideoItemsFlow(): Flow<List<VideoItem>> = context.queryVideoItemsAsFlow()

    override fun getLocalPlayerItems(): List<PlayerItem> {
        return context.queryVideoItems().map { it.toPlayerItem() }
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
