package dev.anilbeesetti.nextplayer.core.data.repository

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.data.util.FileManager
import dev.anilbeesetti.nextplayer.core.data.util.VideoItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class VideoRepositoryImpl @Inject constructor(
    private val fileManager: FileManager
) : VideoRepository {
    override fun getVideoItemsFlow(): Flow<List<VideoItem>> = fileManager.getVideoItemsFlow()

    override fun getAllVideoPaths(): List<String> = fileManager.getAllVideosDataColumn()

    override fun getPath(contentUri: Uri): String? = fileManager.getPath(contentUri)
}
