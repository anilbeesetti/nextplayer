package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.data.util.MediaManager
import dev.anilbeesetti.nextplayer.core.data.util.VideoItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class VideoRepositoryImpl @Inject constructor(
    private val mediaManager: MediaManager
): VideoRepository {
    override fun getVideos(): Flow<List<VideoItem>> = mediaManager.getVideosFlow()
}