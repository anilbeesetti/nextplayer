package dev.anilbeesetti.nextplayer.core.data.mediasource

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.data.models.Video
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class LocalMediaSource @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaSource {

    override fun getVideoItemsFlow(
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Flow<List<Video>> = callbackFlow {
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

    override fun getVideoItems(
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): List<Video> {
        val videos = mutableListOf<Video>()
        context.contentResolver.query(
            VIDEO_COLLECTION_URI,
            VIDEO_PROJECTION,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                videos.add(cursor.toVideo)
            }
        }
        return videos
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
        MediaStore.Video.Media.WIDTH
    )

/**
 * convert cursor to video item
 * @see Video
 */
private inline val Cursor.toVideo: Video
    get() {
        val id = getLong(this.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
        val path = getString(this.getColumnIndexOrThrow(MediaStore.Video.Media.DATA))
        val duration = getLong(this.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION))
        val width = getInt(this.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH))
        val height = getInt(this.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH))
        val file = File(path)
        return Video(
            id = id,
            path = path,
            duration = duration,
            uriString = ContentUris.withAppendedId(VIDEO_COLLECTION_URI, id).toString(),
            displayName = file.nameWithoutExtension,
            nameWithExtension = file.name,
            width = width,
            height = height
        )
    }
