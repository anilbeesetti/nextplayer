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
import androidx.core.text.isDigitsOnly
import dev.anilbeesetti.nextplayer.core.data.models.PlayerItem
import dev.anilbeesetti.nextplayer.core.data.models.VideoItem
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

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
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.HEIGHT,
        MediaStore.Video.Media.WIDTH,
        MediaStore.Video.Media.DISPLAY_NAME
    )

/**
 * get path from uri
 * @param uri uri of the file
 * @return path of the file
 */
fun Context.getPath(uri: Uri): String? {
    if (DocumentsContract.isDocumentUri(this, uri)) {
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
                if (docId.isDigitsOnly()) {
                    return try {
                        val contentUri = ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"),
                            docId.toLong()
                        )
                        getDataColumn(contentUri, null, null)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            uri.isMediaDocument -> {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                val type = split[0]
                var contentUri: Uri? = null
                when (type) {
                    "image" -> contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val selection = "_id=?"
                val selectionArgs = arrayOf(
                    split[1]
                )
                return contentUri?.let { getDataColumn(it, selection, selectionArgs) }
            }
        }
    } else if ("content".equals(uri.scheme, ignoreCase = true)) {
        return if (uri.isGooglePhotosUri) {
            uri.lastPathSegment
        } else {
            getDataColumn(uri, null, null)
        }
    } else if ("file".equals(uri.scheme, ignoreCase = true)) {
        return uri.path
    }
    return null
}

/**
 * get data column from uri
 * @param uri uri of the file
 * @param selection selection
 * @param selectionArgs selection arguments
 * @return data column
 */
private fun Context.getDataColumn(
    uri: Uri,
    selection: String? = null,
    selectionArgs: Array<String>? = null
): String? {
    var cursor: Cursor? = null
    val column = MediaStore.Images.Media.DATA
    val projection = arrayOf(column)
    try {
        cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        if (cursor != null && cursor.moveToFirst()) {
            val index = cursor.getColumnIndexOrThrow(column)
            return cursor.getString(index)
        }
    } finally {
        cursor?.close()
    }
    return null
}

/**
 * query all video items from media store
 * @param selection selection of the query
 * @param selectionArgs selection arguments of the query
 * @param sortOrder sort order of the query
 * @return flow of list of video items
 * @see VideoItem
 */
fun Context.queryVideoItemsAsFlow(
    selection: String? = null,
    selectionArgs: Array<String>? = null,
    sortOrder: String? = null
) = callbackFlow {
    val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            trySend(queryVideoItems(selection, selectionArgs, sortOrder))
        }
    }
    contentResolver.registerContentObserver(VIDEO_COLLECTION_URI, true, observer)
    // initial value
    trySend(queryVideoItems(selection, selectionArgs, sortOrder))
    // close
    awaitClose { contentResolver.unregisterContentObserver(observer) }
}.distinctUntilChanged()

/**
 * query all video items from media store
 * @param selection selection of the query
 * @param selectionArgs selection arguments of the query
 * @param sortOrder sort order of the query
 * @return list of video items
 * @see VideoItem
 */
fun Context.queryVideoItems(
    selection: String? = null,
    selectionArgs: Array<String>? = null,
    sortOrder: String? = null
): List<VideoItem> {
    val videoItems = mutableListOf<VideoItem>()
    contentResolver.query(
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

/**
 * query local player items from media store
 * @return list of player items
 * @see PlayerItem
 */
fun Context.queryLocalPlayerItems(): List<PlayerItem> {
    val playerItems = mutableListOf<PlayerItem>()

    contentResolver.query(
        VIDEO_COLLECTION_URI,
        arrayOf(MediaStore.Video.Media.DATA, MediaStore.Video.Media.DURATION),
        null,
        null,
        null
    )?.use { cursor ->
        while (cursor.moveToNext()) {
            playerItems.add(
                PlayerItem(
                    mediaPath = cursor.getString(
                        cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                    ),
                    duration = cursor.getLong(
                        cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    )
                )
            )
        }
    }

    return playerItems
}

/**
 * convert cursor to video item
 * @see VideoItem
 */
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
