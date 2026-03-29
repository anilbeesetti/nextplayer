package dev.anilbeesetti.nextplayer.core.media.services

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.extensions.VIDEO_COLLECTION_URI
import dev.anilbeesetti.nextplayer.core.common.extensions.prettyName
import dev.anilbeesetti.nextplayer.core.model.FolderFilter
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

    override fun observeFolders(filter: FolderFilter): Flow<List<MediaFolder>> {
        return context.contentObserverFlow()
            .map { fetchFolders(filter) }
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()
    }

    override fun observeVideos(filter: FolderFilter): Flow<List<MediaVideo>> {
        return context.contentObserverFlow()
            .map { fetchVideos(filter) }
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()
    }

    override suspend fun fetchFolders(filter: FolderFilter): List<MediaFolder> = withContext(Dispatchers.IO) {
        val mediaVideos = fetchAllDescendantVideos(filter)
        val videosByActualFolder = mediaVideos.groupBy { File(it.path).parentFile }
        val videosByDisplayFolder = groupVideosByDisplayFolder(mediaVideos, filter, videosByActualFolder)
        val actualFolderPaths = videosByActualFolder.keys.filterNotNull().map { it.path }

        return@withContext videosByDisplayFolder.mapNotNull { (folderFile, videos) ->
            folderFile?.toMediaFolder(
                videos = videos,
                filter = filter,
                actualFolderPaths = actualFolderPaths,
                videosByActualFolder = videosByActualFolder,
            )
        }
    }

    /**
     * Fetches all videos that are descendants of the filter path (ignoring directChildrenOnly).
     */
    private suspend fun fetchAllDescendantVideos(filter: FolderFilter): List<MediaVideo> {
        val allDescendantsFilter = when (filter) {
            is FolderFilter.WithPath -> filter.copy(directChildrenOnly = false)
            FolderFilter.All -> FolderFilter.All
        }
        return fetchVideos(allDescendantsFilter)
    }

    /**
     * Groups videos by their display folder.
     *
     * For FOLDER_TREE mode with directChildrenOnly=true, videos in nested folders
     * are grouped under their nearest ancestor that is a direct child of the filter path.
     * Otherwise, videos are grouped by their actual parent folder.
     */
    private fun groupVideosByDisplayFolder(
        videos: List<MediaVideo>,
        filter: FolderFilter,
        videosByActualFolder: Map<File?, List<MediaVideo>>,
    ): Map<File?, List<MediaVideo>> {
        return if (filter is FolderFilter.WithPath && filter.directChildrenOnly) {
            videos.groupBy { video ->
                File(video.path).walkUp().firstOrNull { it.parent == filter.folderPath }
            }
        } else {
            videosByActualFolder
        }
    }

    /**
     * Converts a folder File to MediaFolder with computed metadata.
     */
    private fun File.toMediaFolder(
        videos: List<MediaVideo>,
        filter: FolderFilter,
        actualFolderPaths: List<String>,
        videosByActualFolder: Map<File?, List<MediaVideo>>,
    ): MediaFolder {
        val nestedFoldersCount = calculateNestedFoldersCount(filter, actualFolderPaths)

        return MediaFolder(
            path = path,
            name = prettyName,
            dateModified = videos.minOfOrNull { it.dateModified } ?: 0L,
            totalSize = videos.sumOf { it.size },
            totalDuration = videos.sumOf { it.duration },
            videosCount = videosByActualFolder[this]?.size ?: 0,
            foldersCount = nestedFoldersCount,
        )
    }

    /**
     * Calculates the number of direct child folders within this folder.
     * Only applicable when displaying folder tree with directChildrenOnly=true.
     */
    private fun File.calculateNestedFoldersCount(
        filter: FolderFilter,
        actualFolderPaths: List<String>,
    ): Int {
        if (filter !is FolderFilter.WithPath || !filter.directChildrenOnly) return 0

        val folderPrefix = path + File.separator
        return actualFolderPaths
            .filter { it.startsWith(folderPrefix) }
            .map { it.removePrefix(folderPrefix).substringBefore(File.separator) }
            .distinct()
            .count()
    }

    override suspend fun fetchVideos(filter: FolderFilter): List<MediaVideo> = withContext(Dispatchers.IO) {
        val mediaVideos = mutableListOf<MediaVideo>()

        val selection = if (filter is FolderFilter.WithPath) "${MediaStore.Video.Media.DATA} LIKE ?" else null
        val selectionArgs = if (filter is FolderFilter.WithPath) arrayOf("${filter.folderPath}/%") else null
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
                if (filter is FolderFilter.WithPath && filter.directChildrenOnly && File(video.path).parent != filter.folderPath) continue
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

        val allVideos = fetchVideos(FolderFilter.WithPath(folderPath = path, directChildrenOnly = false))

        val directVideos = allVideos.filter { File(it.path).parent == path }
        val directSubfolders = allVideos
            .mapNotNull { video ->
                File(video.path).walkUp().firstOrNull { it.parent == path }
            }
            .distinctBy { it.path }

        return@withContext MediaFolder(
            path = folderFile.path,
            name = folderFile.prettyName,
            dateModified = allVideos.minOfOrNull { it.dateModified } ?: folderFile.lastModified(),
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
