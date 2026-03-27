package dev.anilbeesetti.nextplayer.core.media.services

import android.net.Uri
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
    val displayName: String,
    val duration: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val dateModified: Long,
)

interface MediaService {
    fun getFolders(folderPath: String? = null): Flow<List<MediaFolder>>
    fun getVideos(folderPath: String? = null): Flow<List<MediaVideo>>

    fun getVideo(uri: Uri): MediaVideo?
}

