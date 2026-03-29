package dev.anilbeesetti.nextplayer.core.media.services

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.extensions.VIDEO_COLLECTION_URI
import dev.anilbeesetti.nextplayer.core.common.extensions.prettyName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * [MediaService] implementation that queries the Android MediaStore for video files.
 *
 * This implementation provides a simple, flat view of media:
 * - Videos are fetched recursively from the specified folder path
 * - Folders are derived from video parent directories
 */
class MediaStoreMediaService @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaService {

    companion object {
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

    override fun observeFolders(folderPath: String): Flow<List<MediaFolder>> {
        return context.contentObserverFlow()
            .map { fetchFolders(folderPath) }
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()
    }

    override fun observeVideos(folderPath: String): Flow<List<MediaVideo>> {
        return context.contentObserverFlow()
            .map { fetchVideos(folderPath) }
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()
    }

    override suspend fun fetchFolders(folderPath: String): List<MediaFolder> = withContext(Dispatchers.IO) {
        val videos = fetchVideos(folderPath)

        val videosByFolder = videos.groupBy { File(it.path).parentFile }

        return@withContext videosByFolder.mapNotNull { (folder, folderVideos) ->
            folder?.let {
                MediaFolder(
                    path = it.path,
                    name = it.prettyName,
                    dateModified = folderVideos.maxOfOrNull { video -> video.dateModified } ?: 0L,
                    totalSize = folderVideos.sumOf { video -> video.size },
                    totalDuration = folderVideos.sumOf { video -> video.duration },
                    videosCount = folderVideos.size,
                    foldersCount = 0,
                )
            }
        }
    }

    override suspend fun fetchVideos(folderPath: String): List<MediaVideo> = withContext(Dispatchers.IO) {
        val mediaVideos = mutableListOf<MediaVideo>()

        val selection = "${MediaStore.Video.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("$folderPath/%")
        val sortOrder = "${MediaStore.Video.Media.DISPLAY_NAME} ASC"

        context.contentResolver.query(
            VIDEO_COLLECTION_URI,
            VIDEO_PROJECTION,
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val video = cursor.toMediaVideo() ?: continue
                mediaVideos.add(video)
            }
        }
        return@withContext mediaVideos
    }

    override suspend fun findVideo(uri: Uri): MediaVideo? = withContext(Dispatchers.IO) {
        return@withContext context.contentResolver.query(
            uri,
            VIDEO_PROJECTION,
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@withContext null
            cursor.toMediaVideo()
        }
    }

    override suspend fun findFolder(path: String): MediaFolder? = withContext(Dispatchers.IO) {
        val folderFile = File(path)
        if (!folderFile.exists()) return@withContext null

        val allVideos = fetchVideos(path)
        if (allVideos.isEmpty()) return@withContext null

        val directVideos = allVideos.filter { File(it.path).parent == path }
        val directSubfolders = allVideos
            .mapNotNull { video ->
                File(video.path).parentFile?.walkUp()?.firstOrNull { it.parent == path }
            }
            .distinctBy { it.path }

        return@withContext MediaFolder(
            path = folderFile.path,
            name = folderFile.prettyName,
            dateModified = allVideos.maxOfOrNull { it.dateModified } ?: folderFile.lastModified(),
            totalSize = allVideos.sumOf { it.size },
            totalDuration = allVideos.sumOf { it.duration },
            videosCount = directVideos.size,
            foldersCount = directSubfolders.size,
        )
    }

    /**
     * Walks up the directory tree from this file's parent to the root.
     */
    private fun File.walkUp() = generateSequence(parentFile) { it.parentFile }

    private fun Cursor.toMediaVideo(): MediaVideo? {
        val path = getString(getColumnIndexOrThrow(MediaStore.Video.Media.DATA))
        val file = File(path)
        if (!file.exists()) return null
        val id = getLong(getColumnIndexOrThrow(MediaStore.Video.Media._ID))
        return MediaVideo(
            id = id,
            path = path,
            title = file.nameWithoutExtension,
            parentPath = file.parent ?: "/",
            uri = ContentUris.withAppendedId(VIDEO_COLLECTION_URI, id),
            displayName = getString(getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)),
            duration = getLong(getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)),
            width = getInt(getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)),
            height = getInt(getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)),
            size = getLong(getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)),
            dateModified = getLong(getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)),
        )
    }

    private fun Context.contentObserverFlow(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        contentResolver.registerContentObserver(VIDEO_COLLECTION_URI, true, observer)
        trySend(Unit)
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }
}
