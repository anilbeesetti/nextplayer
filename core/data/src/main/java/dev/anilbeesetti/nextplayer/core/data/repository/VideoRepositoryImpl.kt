package dev.anilbeesetti.nextplayer.core.data.repository

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.data.util.FileManager
import dev.anilbeesetti.nextplayer.core.data.util.VideoItem
import dev.anilbeesetti.nextplayer.core.database.dao.VideoDao
import dev.anilbeesetti.nextplayer.core.database.entities.VideoEntity
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class VideoRepositoryImpl @Inject constructor(
    private val fileManager: FileManager,
    private val videoDao: VideoDao
) : VideoRepository {

    private val scope = CoroutineScope(Dispatchers.IO)
    override fun getVideoItemsFlow(): Flow<List<VideoItem>> = fileManager.getVideoItemsFlow()

    override fun getAllVideoPaths(): List<String> = fileManager.getAllVideosDataColumn()

    override fun getPath(contentUri: Uri): String? = fileManager.getPath(contentUri)

    override suspend fun getPosition(path: String): Long? {
        return videoDao.get(path)?.playbackPosition
    }

    override fun updatePosition(path: String, position: Long) {
        scope.launch {
            videoDao.upsert(
                VideoEntity(
                    path = path,
                    playbackPosition = position
                )
            )
        }
    }
}
