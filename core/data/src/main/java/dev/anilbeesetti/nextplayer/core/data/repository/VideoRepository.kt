package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.data.models.PlayerItem
import dev.anilbeesetti.nextplayer.core.data.models.Video
import kotlinx.coroutines.flow.Flow

interface VideoRepository {

    /**
     * Get list of [Video]s as flow
     * @return flow of list of video items
     */
    fun getVideosFlow(): Flow<List<Video>>

    /**
     * Get local player items
     * @return list of player items
     */
    fun getLocalPlayerItems(): List<PlayerItem>

    /**
     * Get position from path
     * @param path path of the video
     * @return position in milliseconds
     */
    suspend fun getPosition(path: String): Long?

    /**
     * Update position of the video
     * @param path path of the video
     * @param position position in milliseconds
     */
    suspend fun updatePosition(path: String, position: Long)
}
