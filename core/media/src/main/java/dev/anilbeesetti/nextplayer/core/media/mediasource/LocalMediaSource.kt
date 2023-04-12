package dev.anilbeesetti.nextplayer.core.media.mediasource

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.media.model.MediaFolder
import dev.anilbeesetti.nextplayer.core.media.model.MediaVideo
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class LocalMediaSource @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaSource {

    override fun getVideoItemsFlow(
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Flow<List<MediaVideo>> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(getVideoItems(selection, selectionArgs, sortOrder))
            }
        }
        context.contentResolver.registerContentObserver(VIDEO_COLLECTION_URI, true, observer)
        // initial value
        trySend(getVideoItems(selection, selectionArgs, sortOrder))
        // close
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.distinctUntilChanged()

    override fun getMediaFoldersFlow(
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Flow<List<MediaFolder>> {
        try {
            return getVideoItemsFlow(selection, selectionArgs, sortOrder).map { videoItems ->
                videoItems.mapNotNull { File(it.data).parentFile }
                    .distinct()
                    .map { file ->
                        MediaFolder(
                            id = file.hashCode().toLong(),
                            name = file.name.takeIf { file.path != "/storage/emulated/0" } ?: "Internal Storage",
                            path = file.path,
                            media = getVideoItems().filter { File(it.data).parentFile == file }
                        )
                    }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return flow { emptyList<MediaFolder>() }
        }
    }

    override fun getVideoItems(
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
        return mediaVideos
    }
}

private val VIDEO_COLLECTION_URI
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

private val VIDEO_PROJECTION
    get() = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DATA,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.HEIGHT,
        MediaStore.Video.Media.WIDTH,
        MediaStore.Video.Media.SIZE
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
            size = getLong(this.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE))
        )
    }
