package dev.anilbeesetti.nextplayer.core.media.sync

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.common.di.ApplicationScope
import dev.anilbeesetti.nextplayer.core.common.extensions.VIDEO_COLLECTION_URI
import dev.anilbeesetti.nextplayer.core.common.extensions.getStorageVolumes
import dev.anilbeesetti.nextplayer.core.common.extensions.prettyName
import dev.anilbeesetti.nextplayer.core.common.extensions.scanPaths
import dev.anilbeesetti.nextplayer.core.common.extensions.scanStorage
import dev.anilbeesetti.nextplayer.core.database.converter.UriListConverter
import dev.anilbeesetti.nextplayer.core.database.dao.DirectoryDao
import dev.anilbeesetti.nextplayer.core.database.dao.MediumDao
import dev.anilbeesetti.nextplayer.core.database.entities.DirectoryEntity
import dev.anilbeesetti.nextplayer.core.database.entities.MediumEntity
import dev.anilbeesetti.nextplayer.core.media.model.MediaVideo
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocalMediaSynchronizer @Inject constructor(
    private val mediumDao: MediumDao,
    private val directoryDao: DirectoryDao,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    @Dispatcher(NextDispatchers.IO) private val dispatcher: CoroutineDispatcher,
) : MediaSynchronizer {

    private var mediaSyncingJob: Job? = null

    override suspend fun refresh(path: String?): Boolean {
        return path?.let { context.scanPaths(listOf(path)) }
            ?: context.getStorageVolumes().all { context.scanStorage(it.path) }
    }

    override fun startSync() {
        if (mediaSyncingJob != null) return
        mediaSyncingJob = getMediaVideosFlow().onEach { media ->
            applicationScope.launch { updateDirectories(media) }
            applicationScope.launch { updateMedia(media) }
        }.launchIn(applicationScope)
    }

    override fun stopSync() {
        mediaSyncingJob?.cancel()
    }

    private suspend fun updateDirectories(media: List<MediaVideo>) =
        withContext(Dispatchers.Default) {
            val directories = context.getStorageVolumes().flatMap {
                getDirectoryEntities(currentFolder = it, media = media)
            }
            directoryDao.upsertAll(directories)

            val currentDirectoryPaths = directories.map { it.path }

            val unwantedDirectories = directoryDao.getAll().first()
                .filterNot { it.path in currentDirectoryPaths }

            val unwantedDirectoriesPaths = unwantedDirectories.map { it.path }

            directoryDao.delete(unwantedDirectoriesPaths)
        }

    private fun getDirectoryEntities(
        parentFolder: File? = null,
        currentFolder: File,
        media: List<MediaVideo>,
    ): List<DirectoryEntity> {
        val hasMediaInCurrentFolder = media.any { it.data.startsWith(currentFolder.path) }

        if (!hasMediaInCurrentFolder) return emptyList()

        val currentDirectoryEntity = DirectoryEntity(
            path = currentFolder.path,
            name = currentFolder.prettyName,
            modified = currentFolder.lastModified(),
            parentPath = parentFolder?.path ?: "/",
        )

        val subDirectories = currentFolder.listFiles { file ->
            file.isDirectory && media.any { it.data.startsWith(file.path) }
        }?.flatMap { file ->
            getDirectoryEntities(
                parentFolder = currentFolder,
                currentFolder = file,
                media = media,
            )
        } ?: emptyList()

        return listOf(currentDirectoryEntity) + subDirectories
    }

    private suspend fun updateMedia(media: List<MediaVideo>) = withContext(Dispatchers.Default) {
        val mediumEntities = media.map {
            val file = File(it.data)
            val mediumEntity = mediumDao.get(it.uri.toString())
            mediumEntity?.copy(
                path = file.path,
                name = file.name,
                size = it.size,
                width = it.width,
                height = it.height,
                duration = it.duration,
                mediaStoreId = it.id,
                modified = it.dateModified,
                parentPath = file.parent!!,
            ) ?: MediumEntity(
                uriString = it.uri.toString(),
                path = it.data,
                name = file.name,
                parentPath = file.parent!!,
                modified = it.dateModified,
                size = it.size,
                width = it.width,
                height = it.height,
                duration = it.duration,
                mediaStoreId = it.id,
            )
        }

        mediumDao.upsertAll(mediumEntities)

        val currentMediaUris = mediumEntities.map { it.uriString }

        val unwantedMedia = mediumDao.getAll().first()
            .filterNot { it.uriString in currentMediaUris }

        val unwantedMediaUris = unwantedMedia.map { it.uriString }

        mediumDao.delete(unwantedMediaUris)

        // Delete unwanted thumbnails
        val unwantedThumbnailFiles = unwantedMedia.mapNotNull { medium -> medium.thumbnailPath?.let { File(it) } }
        unwantedThumbnailFiles.forEach { file ->
            try {
                file.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Release external subtitle uri permission if not used by any other media
        launch {
            val currentMediaExternalSubs = mediumEntities.flatMap { UriListConverter.fromStringToList(it.externalSubs) }.toSet()

            unwantedMedia.onEach {
                for (sub in UriListConverter.fromStringToList(it.externalSubs)) {
                    if (sub !in currentMediaExternalSubs) {
                        try {
                            context.contentResolver.releasePersistableUriPermission(sub, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    private fun getMediaVideosFlow(
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = "${MediaStore.Video.Media.DISPLAY_NAME} ASC",
    ): Flow<List<MediaVideo>> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(getMediaVideo(selection, selectionArgs, sortOrder))
            }
        }
        context.contentResolver.registerContentObserver(VIDEO_COLLECTION_URI, true, observer)
        // initial value
        trySend(getMediaVideo(selection, selectionArgs, sortOrder))
        // close
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.flowOn(dispatcher).distinctUntilChanged()

    private fun getMediaVideo(
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): List<MediaVideo> {
        val mediaVideos = mutableListOf<MediaVideo>()
        context.contentResolver.query(
            VIDEO_COLLECTION_URI,
            VIDEO_PROJECTION,
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->

            val idColumn = cursor.getColumnIndex(MediaStore.Video.Media._ID)
            val dataColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
            val durationColumn = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
            val widthColumn = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
            val heightColumn = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
            val sizeColumn = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
            val dateModifiedColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                mediaVideos.add(
                    MediaVideo(
                        id = id,
                        data = cursor.getString(dataColumn),
                        duration = cursor.getLong(durationColumn),
                        uri = ContentUris.withAppendedId(VIDEO_COLLECTION_URI, id),
                        width = cursor.getInt(widthColumn),
                        height = cursor.getInt(heightColumn),
                        size = cursor.getLong(sizeColumn),
                        dateModified = cursor.getLong(dateModifiedColumn),
                    ),
                )
            }
        }
        return mediaVideos.filter { File(it.data).exists() }
    }

    companion object {
        val VIDEO_PROJECTION = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
        )
    }
}
