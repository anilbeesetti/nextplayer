package dev.anilbeesetti.nextplayer.core.media.services

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.model.FolderFilter
import kotlinx.coroutines.flow.Flow

data class MediaFolder(
    val path: String,
    val name: String,
    val dateModified: Long,
    val totalSize: Long,
    val totalDuration: Long,
    val videosCount: Int,
    val foldersCount: Int,
)

data class MediaVideo(
    val id: Long,
    val uri: Uri,
    val path: String,
    val title: String,
    val parentPath: String,
    val displayName: String,
    val duration: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val dateModified: Long,
)

interface MediaService {
    fun observeFolders(filter: FolderFilter): Flow<List<MediaFolder>>
    fun observeVideos(filter: FolderFilter): Flow<List<MediaVideo>>
    suspend fun fetchFolders(filter: FolderFilter): List<MediaFolder>
    suspend fun fetchVideos(filter: FolderFilter): List<MediaVideo>
    suspend fun findVideo(uri: Uri): MediaVideo?
    suspend fun findFolder(path: String): MediaFolder?
}

