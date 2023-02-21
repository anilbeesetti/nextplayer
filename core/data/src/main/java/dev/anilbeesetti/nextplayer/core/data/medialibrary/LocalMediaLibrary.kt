package dev.anilbeesetti.nextplayer.core.data.medialibrary

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.data.models.VideoItem
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class LocalMediaLibrary @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaLibrary {

    override fun getVideoItemsFlow(
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Flow<List<VideoItem>> = callbackFlow {
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
    ): List<VideoItem> {
        val videoItems = mutableListOf<VideoItem>()
        context.contentResolver.query(
            VIDEO_COLLECTION_URI,
            VIDEO_PROJECTION,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                videoItems.add(cursor.toVideoItem)
            }
        }
        return videoItems
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
        MediaStore.Video.Media.TITLE,
        MediaStore.Video.Media.DATA,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.HEIGHT,
        MediaStore.Video.Media.WIDTH,
        MediaStore.Video.Media.DISPLAY_NAME
    )

/**
 * convert cursor to video item
 * @see VideoItem
 */
private inline val Cursor.toVideoItem: VideoItem
    get() {
        val id = getLong(this.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
        return VideoItem(
            id = id,
            path = getString(this.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)),
            duration = getLong(this.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)),
            contentUri = ContentUris.withAppendedId(VIDEO_COLLECTION_URI, id),
            displayName = getString(this.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)),
            nameWithExtension = getString(this.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)),
            width = getInt(this.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)),
            height = getInt(this.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH))
        )
    }
