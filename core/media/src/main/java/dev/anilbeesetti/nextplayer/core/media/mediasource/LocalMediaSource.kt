package dev.anilbeesetti.nextplayer.core.media.mediasource

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.common.extensions.VIDEO_COLLECTION_URI
import dev.anilbeesetti.nextplayer.core.media.model.MediaVideo
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

class LocalMediaSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(NextDispatchers.IO) private val dispatcher: CoroutineDispatcher
) : MediaSource {

    override fun getMediaVideosFlow(
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
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

    override fun getMediaVideo(
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
