package io.github.anilbeesetti.nextlib.mediainfo

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore

internal object PathUtil {

    fun getPath(context: Context, uri: Uri): String? {
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
                    return try {
                        val contentUri = ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"),
                            docId.toLong()
                        )
                        getDataColumn(context, contentUri, null, null)
                    } catch (e: Exception) {
                        null
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
                    return contentUri?.let { getDataColumn(context, it, selection, selectionArgs) }
                }
            }
        } else if (ContentResolver.SCHEME_CONTENT.equals(uri.scheme, ignoreCase = true)) {
            return if (uri.isGooglePhotosUri) {
                uri.lastPathSegment
            } else {
                getDataColumn(context, uri, null, null)
            }
        } else if (ContentResolver.SCHEME_FILE.equals(uri.scheme, ignoreCase = true)) {
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
    private fun getDataColumn(
        context: Context,
        uri: Uri,
        selection: String? = null,
        selectionArgs: Array<String>? = null
    ): String? {
        val column = MediaStore.Images.Media.DATA
        val projection = arrayOf(column)
        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndexOrThrow(column)
                    return cursor.getString(index)
                }
            }
        } catch (e: Exception) {
            return null
        }
        return null
    }

    /**
     * Whether the Uri authority is ExternalStorageProvider.
     */
    val Uri.isExternalStorageDocument: Boolean
        get() = "com.android.externalstorage.documents" == authority

    /**
     * Whether the Uri authority is DownloadsProvider.
     */
    val Uri.isDownloadsDocument: Boolean
        get() = "com.android.providers.downloads.documents" == authority

    /**
     * Whether the Uri authority is MediaProvider.
     */
    val Uri.isMediaDocument: Boolean
        get() = "com.android.providers.media.documents" == authority

    /**
     * Whether the Uri authority is Google Photos.
     */
    val Uri.isGooglePhotosUri: Boolean
        get() = "com.google.android.apps.photos.content" == authority
}