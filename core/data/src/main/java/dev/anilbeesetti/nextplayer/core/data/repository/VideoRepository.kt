package dev.anilbeesetti.nextplayer.core.data.repository

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.data.models.PlayerItem
import dev.anilbeesetti.nextplayer.core.data.models.VideoItem
import kotlinx.coroutines.flow.Flow

interface VideoRepository {

    /**
     * Get videos flow
     * @return flow of list of video items
     */
    fun getVideoItemsFlow(): Flow<List<VideoItem>>

    /**
     * Get local player items
     * @return list of player items
     */
    fun getLocalPlayerItems(): List<PlayerItem>


    /**
     * Get path from content uri
     * @param contentUri content uri of the video
     * @return path of the video
     */
    fun getPath(contentUri: Uri): String?


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
