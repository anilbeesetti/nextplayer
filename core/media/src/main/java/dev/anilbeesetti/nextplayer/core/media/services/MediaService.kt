package dev.anilbeesetti.nextplayer.core.media.services

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.extensions.prettyName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject

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
}

class MediaStoreMediaService @Inject constructor(
    @ApplicationContext private val context: Context,
): MediaService {

    companion object {
        private val VIDEO_COLLECTION_URI = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        private val VIDEO_PROJECTION = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
        )
    }

    override fun getFolders(folderPath: String?): Flow<List<MediaFolder>> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(getMediaFolders(folderPath = folderPath, directChildrenOnly = folderPath != null))
            }
        }
        context.contentResolver.registerContentObserver(VIDEO_COLLECTION_URI, true, observer)
        // initial value
        trySend(getMediaFolders(folderPath = folderPath, directChildrenOnly = folderPath != null))
        // close
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.flowOn(Dispatchers.IO).distinctUntilChanged()


    override fun getVideos(folderPath: String?): Flow<List<MediaVideo>> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(getMediaVideos(folderPath = folderPath, directChildrenOnly = folderPath != null))
            }
        }
        context.contentResolver.registerContentObserver(VIDEO_COLLECTION_URI, true, observer)
        // initial value
        trySend(getMediaVideos(folderPath = folderPath, directChildrenOnly = folderPath != null))
        // close
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.flowOn(Dispatchers.IO).distinctUntilChanged()


    private fun getMediaVideos(
        folderPath: String?,
        directChildrenOnly: Boolean
    ): List<MediaVideo> {
        val mediaVideos = mutableListOf<MediaVideo>()

        val selection = "${MediaStore.Video.Media.DATA} LIKE ?".takeIf { folderPath != null }
        val selectionArgs = arrayOf("$folderPath/%").takeIf { folderPath != null }
        val sortOrder = "${MediaStore.Video.Media.DISPLAY_NAME} ASC"

        context.contentResolver.query(
            VIDEO_COLLECTION_URI,
            VIDEO_PROJECTION,
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndex(MediaStore.Video.Media._ID)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
            val durationColumn = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
            val widthColumn = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
            val heightColumn = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
            val sizeColumn = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
            val dateModifiedColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val path = cursor.getString(dataColumn)
                val file = File(path)

                if (!file.exists()) continue
                if (directChildrenOnly && file.parent != folderPath) continue

                mediaVideos.add(
                    MediaVideo(
                        id = id,
                        path = path,
                        uri = ContentUris.withAppendedId(VIDEO_COLLECTION_URI, id),
                        title = file.nameWithoutExtension,
                        displayName = cursor.getString(displayNameColumn),
                        duration = cursor.getLong(durationColumn),
                        width = cursor.getInt(widthColumn),
                        height = cursor.getInt(heightColumn),
                        size = cursor.getLong(sizeColumn),
                        dateModified = cursor.getLong(dateModifiedColumn),
                    ),
                )
            }
        }
        return mediaVideos
    }

    private fun getMediaFolders(
        folderPath: String?,
        directChildrenOnly: Boolean
    ): List<MediaFolder> {
        val mediaVideos = getMediaVideos(folderPath = folderPath, directChildrenOnly = false)
        val videosByActualFolder = mediaVideos.groupBy { File(it.path).parentFile }
        val videosByFolder = if (directChildrenOnly && folderPath != null) {
            mediaVideos.groupBy { video ->
                File(video.path).walkUp().firstOrNull { it.parent == folderPath }
            }
        } else {
            videosByActualFolder
        }

        return videosByFolder.mapNotNull { (folderFile, videos) ->
            if (folderFile == null) return@mapNotNull null
            if (directChildrenOnly && folderFile.parent != folderPath) return@mapNotNull null

            MediaFolder(
                path = folderFile.path,
                name = folderFile.prettyName,
                dateModified = videos.minOf { it.dateModified },
                totalSize = videos.sumOf { it.size },
                totalDuration = videos.sumOf { it.duration },
                videosCount = videosByActualFolder[folderFile]?.size ?: 0,
                foldersCount = 1,
            )
        }.also {
            it.forEach {
                println(it)
            }
        }
    }

    private fun File.walkUp() = generateSequence(parentFile) { it.parentFile }
}

