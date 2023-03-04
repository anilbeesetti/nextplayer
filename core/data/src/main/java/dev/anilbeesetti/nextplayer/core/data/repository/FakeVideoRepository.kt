package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.data.models.PlayerItem
import dev.anilbeesetti.nextplayer.core.data.models.Video
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeVideoRepository : VideoRepository {

    val videoItems = mutableListOf<Video>()
    val pathPositionMap = mutableMapOf<String, Long>()

    override fun getVideosFlow(): Flow<List<Video>> {
        return flowOf(videoItems)
    }

    override fun getLocalPlayerItems(): List<PlayerItem> {
        return videoItems.map { it.toPlayerItem() }
    }

    override suspend fun getPosition(path: String): Long? {
        return pathPositionMap[path]
    }

    override suspend fun updatePosition(path: String, position: Long) {
        pathPositionMap[path] = position
    }
}
