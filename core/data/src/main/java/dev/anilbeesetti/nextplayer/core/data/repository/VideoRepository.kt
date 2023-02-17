package dev.anilbeesetti.nextplayer.core.data.repository

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.data.util.VideoItem
import kotlinx.coroutines.flow.Flow

interface VideoRepository {

    /**
     * Get videos flow
     */
    fun getVideoItemsFlow(): Flow<List<VideoItem>>

    fun getAllVideoPaths(): List<String>

    fun getPath(contentUri: Uri): String?

    suspend fun getPosition(path: String): Long?

    fun updatePosition(path: String, position: Long)
}
