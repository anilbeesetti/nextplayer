package dev.anilbeesetti.nextplayer.core.data.repository

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.data.util.PlayerItem
import dev.anilbeesetti.nextplayer.core.data.util.VideoItem
import kotlinx.coroutines.flow.Flow

interface VideoRepository {

    /**
     * Get videos flow
     */
    fun getVideoItemsFlow(): Flow<List<VideoItem>>

    fun getLocalPlayerItems(): List<PlayerItem>

    fun getPath(contentUri: Uri): String?

    suspend fun getPosition(path: String): Long?

    suspend fun updatePosition(path: String, position: Long)
}
