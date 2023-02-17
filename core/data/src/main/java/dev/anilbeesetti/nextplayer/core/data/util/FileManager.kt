package dev.anilbeesetti.nextplayer.core.data.util

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

@Singleton
class FileManager @Inject constructor(
    @ApplicationContext val context: Context
) {

    fun getPath(uri: Uri): String? {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            when {
                uri.isExternalStorageDocument -> {

                    val docId = DocumentsContract.getDocumentId(uri)
                    val split = docId
                        .split(":".toRegex())
                        .dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                    val type = split[0]
                    if ("primary".equals(type, ignoreCase = true)) {
                        return Environment.getExternalStorageDirectory().path + "/" + split[1]
                    }

                    // TODO handle non-primary volumes
                }
                uri.isDownloadsDocument -> {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val id = docId.split(":")[1]
                    val contentUri = ContentUris.withAppendedId(DOWNLOAD_COLLECTION_URI, id.toLong())
                    return context.contentResolver.getDataColumn(contentUri, null, null)
                }
                uri.isMediaDocument -> {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                    val type = split[0]
                    var contentUri: Uri? = null
                    if (("image" == type)) {
                        contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    } else if (("video" == type)) {
                        contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    } else if (("audio" == type)) {
                        contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                    val selection = "_id=?"
                    val selectionArgs = arrayOf(
                        split[1]
                    )
                    return contentUri?.let { context.contentResolver.getDataColumn(it, selection, selectionArgs) }
                }
            }
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
            return if (uri.isGooglePhotosUri)
                uri.lastPathSegment
            else
                context.contentResolver.getDataColumn(uri, null, null)
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }
        return null
    }

    fun getAllVideosDataColumn(): List<String> {
        val videos = mutableListOf<String>()

        val cursor = context.contentResolver.query(VIDEO_COLLECTION_URI, arrayOf(MediaStore.Video.Media.DATA), null, null, null)
        if (cursor != null) {
            while (cursor.moveToNext()) {
                videos.add(
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA))
                )
            }
            cursor.close()
        }

        return videos
    }

    fun getVideoItemsFlow() = callbackFlow<List<VideoItem>> {
        // Create a content resolver
        val contentResolver = context.contentResolver

        fun updateVideos() {
            val videoItems = mutableListOf<VideoItem>()

            // Perform the query
            val cursor = contentResolver.query(VIDEO_COLLECTION_URI, VIDEO_PROJECTION, null, null, null)

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
        contentResolver.registerContentObserver(VIDEO_COLLECTION_URI, true, observer)

        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }.distinctUntilChanged()
}

private val VIDEO_COLLECTION_URI
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

private val DOWNLOAD_COLLECTION_URI
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        Uri.parse("content://downloads/public_downloads")
    }

private val VIDEO_PROJECTION
    get() = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.TITLE,
        MediaStore.Video.Media.DURATION,
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
            contentUri = ContentUris.withAppendedId(VIDEO_COLLECTION_URI, id),
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
