package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.data.util.VideoItem
import kotlinx.coroutines.flow.Flow

interface VideoRepository {

    /**
     * Get videos flow
     */
    fun getVideos(): Flow<List<VideoItem>>
}
