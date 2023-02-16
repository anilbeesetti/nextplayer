package dev.anilbeesetti.nextplayer.core.data.util

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class MediaManager @Inject constructor(
    @ApplicationContext val context: Context
) {

    fun getVideosFlow() = callbackFlow<List<VideoItem>> {
        // Create a content resolver
        val contentResolver = context.contentResolver

        fun updateVideos() {
            val videoItems = mutableListOf<VideoItem>()

            // Perform the query
            val cursor = contentResolver.query(COLLECTION_URI, VIDEO_PROJECTION, null, null, null)

            // Iterate through the cursor to retrieve the video data
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    videoItems.add(cursor.toVideoItem)
                }
                cursor.close()
            }
            trySend(videoItems)
        }

        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                updateVideos()
            }
        }

        updateVideos()
        contentResolver.registerContentObserver(COLLECTION_URI, true, observer)

        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }.distinctUntilChanged()
}

private val COLLECTION_URI
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

private val VIDEO_PROJECTION
    get() = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.TITLE,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.DATA,
        MediaStore.Video.Media.HEIGHT,
        MediaStore.Video.Media.WIDTH,
        MediaStore.Video.Media.DISPLAY_NAME
    )

private inline val Cursor.toVideoItem: VideoItem
    get() {
        val id = getLong(this.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
        return VideoItem(
            id = id,
            duration = getInt(this.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)),
            contentUri = ContentUris.withAppendedId(COLLECTION_URI, id),
            displayName = getString(this.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)),
            nameWithExtension = getString(this.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)),
            width = getInt(this.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)),
            height = getInt(this.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH))
        )
    }

data class VideoItem(
    val id: Long,
    val duration: Int,
    val contentUri: Uri,
    val displayName: String,
    val nameWithExtension: String,
    val width: Int,
    val height: Int
)
