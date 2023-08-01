package dev.anilbeesetti.nextplayer.core.media.sync

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.common.di.ApplicationScope
import dev.anilbeesetti.nextplayer.core.common.extensions.VIDEO_COLLECTION_URI
import dev.anilbeesetti.nextplayer.core.common.extensions.prettyName
import dev.anilbeesetti.nextplayer.core.database.dao.DirectoryDao
import dev.anilbeesetti.nextplayer.core.database.dao.MediumDao
import dev.anilbeesetti.nextplayer.core.database.entities.DirectoryEntity
import dev.anilbeesetti.nextplayer.core.database.entities.MediumEntity
import dev.anilbeesetti.nextplayer.core.media.model.MediaVideo
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
import java.io.File
import javax.inject.Inject

class LocalMediaSynchronizer @Inject constructor(
    private val mediumDao: MediumDao,
    private val directoryDao: DirectoryDao,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    @Dispatcher(NextDispatchers.IO) private val dispatcher: CoroutineDispatcher
) : MediaSynchronizer {

    private var mediaSyncingJob: Job? = null

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

    private suspend fun updateDirectories(media: List<MediaVideo>) = withContext(
        Dispatchers.Default
    ) {
        val directories = media.groupBy { File(it.data).parentFile!! }.map { (file, videos) ->
            DirectoryEntity(
                path = file.path,
                name = file.prettyName,
                mediaCount = videos.size,
                size = videos.sumOf { it.size },
                modified = file.lastModified()
            )
        }
        directoryDao.upsertAll(directories)

        val currentDirectoryPaths = directories.map { it.path }

        val unwantedDirectories = directoryDao.getAll().first()
            .map { it.path }
            .filterNot { it in currentDirectoryPaths }

        directoryDao.delete(unwantedDirectories)
    }

    private suspend fun updateMedia(media: List<MediaVideo>) = withContext(Dispatchers.Default) {
        val mediumEntities = media.map {
            val file = File(it.data)
            val mediumEntity = mediumDao.get(it.data)
            MediumEntity(
                path = it.data,
                uriString = it.uri.toString(),
                name = file.name,
                parentPath = file.parent!!,
                modified = it.dateModified,
                size = it.size,
                width = it.width,
                height = it.height,
                duration = it.duration,
                mediaStoreId = it.id,
                playbackPosition = mediumEntity?.playbackPosition ?: 0,
                audioTrackIndex = mediumEntity?.audioTrackIndex,
                subtitleTrackIndex = mediumEntity?.subtitleTrackIndex,
                playbackSpeed = mediumEntity?.playbackSpeed
            )
        }

        mediumDao.upsertAll(mediumEntities)

        val currentMediaPaths = mediumEntities.map { it.path }

        val unwantedMedia = mediumDao.getAll().first()
            .map { it.path }
            .filterNot { it in currentMediaPaths }

        mediumDao.delete(unwantedMedia)
    }

    private fun getMediaVideosFlow(
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
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
        sortOrder: String?
    ): List<MediaVideo> {
        val mediaVideos = mutableListOf<MediaVideo>()
        context.contentResolver.query(
            VIDEO_COLLECTION_URI,
            VIDEO_PROJECTION,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                mediaVideos.add(cursor.toMediaVideo)
            }
        }
        return mediaVideos.filter { File(it.data).exists() }
    }
}


private val VIDEO_PROJECTION
    get() = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DATA,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.HEIGHT,
        MediaStore.Video.Media.WIDTH,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.DATE_MODIFIED
    )

/**
 * convert cursor to video item
 * @see MediaVideo
 */
private inline val Cursor.toMediaVideo: MediaVideo
    get() {
        val id = getLong(this.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
        return MediaVideo(
            id = id,
            data = getString(this.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)),
            duration = getLong(this.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)),
            uri = ContentUris.withAppendedId(VIDEO_COLLECTION_URI, id),
            width = getInt(this.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)),
            height = getInt(this.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)),
            size = getLong(this.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)),
            dateModified = getLong(this.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED))
        )
    }
