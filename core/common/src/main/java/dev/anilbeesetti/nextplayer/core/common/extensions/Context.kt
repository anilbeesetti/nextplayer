package dev.anilbeesetti.nextplayer.core.common.extensions

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.text.isDigitsOnly
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

val VIDEO_COLLECTION_URI: Uri
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

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
    } else if (ContentResolver.SCHEME_CONTENT.equals(uri.scheme, ignoreCase = true)) {
        return if (uri.isGooglePhotosUri) {
            uri.lastPathSegment
        } else {
            getDataColumn(uri, null, null)
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
    } catch (e: Exception) {
        return null
    } finally {
        cursor?.close()
    }
    return null
}

/**
 * get filename from uri
 * @param uri uri of the file
 * @return filename of the file
 */
fun Context.getFilenameFromUri(uri: Uri): String {
    return if (ContentResolver.SCHEME_FILE.equals(uri.scheme, ignoreCase = true)) {
        File(uri.toString()).name
    } else {
        getFilenameFromContentUri(uri) ?: uri.lastPathSegment ?: ""
    }
}

/**
 * get filename from content uri
 * @param uri uri of the file
 * @return filename of the file
 */
fun Context.getFilenameFromContentUri(uri: Uri): String? {
    val projection = arrayOf(
        OpenableColumns.DISPLAY_NAME
    )

    try {
        val cursor = contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        }
    } catch (e: Exception) {
        return null
    }
    return null
}

fun Context.getMediaContentUri(uri: Uri): Uri? {
    val path = getPath(uri) ?: return null

    var cursor: Cursor? = null
    val column = MediaStore.Video.Media._ID
    val projection = arrayOf(column)
    try {
        cursor = contentResolver.query(
            VIDEO_COLLECTION_URI,
            projection,
            "${MediaStore.Images.Media.DATA} = ?",
            arrayOf(path),
            null
        )
        if (cursor != null && cursor.moveToFirst()) {
            val index = cursor.getColumnIndexOrThrow(column)
            val id = cursor.getLong(index)
            return ContentUris.withAppendedId(VIDEO_COLLECTION_URI, id)
        }
    } catch (e: Exception) {
        return null
    } finally {
        cursor?.close()
    }
    return null
}

fun Context.showToast(string: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, string, duration).show()
}

fun Context.scanPaths(paths: List<String>, callback: ((String?, Uri?) -> Unit)? = null) {
    MediaScannerConnection.scanFile(this, paths.toTypedArray(), arrayOf("video/*"), callback)
}

fun Context.scanPath(file: File) {
    if (file.isDirectory) {
        file.listFiles()?.forEach { scanPath(it) }
    } else {
        scanPaths(listOf(file.path))
    }
}

fun Context.scanStorage(callback: ((String?, Uri?) -> Unit)? = null) {
    val storagePath = Environment.getExternalStorageDirectory()?.path
    if (storagePath != null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scanPaths(listOf(storagePath), callback)
        } else {
            scanPath(File(storagePath))
        }
    } else {
        callback?.invoke(null, null)
    }
}

fun Context.convertToUTF8(uri: Uri, charset: Charset?): Uri {
    try {
        if (uri.scheme?.lowercase()?.startsWith("http") == true) return uri
        contentResolver.openInputStream(uri)?.use { inputStream ->
            val bufferedInputStream = BufferedInputStream(inputStream)
            val detectedCharset = charset ?: detectCharset(bufferedInputStream)
            return convertToUTF8(uri, bufferedInputStream.reader(detectedCharset))
        }
    } catch (exception: Exception) {
        exception.printStackTrace()
    }
    return uri
}

fun detectCharset(inputStream: BufferedInputStream): Charset {
    val bufferSize = 8000
    inputStream.mark(bufferSize)
    val rawInput = ByteArray(bufferSize)

    var rawLength = 0
    var remainingLength = bufferSize
    while (remainingLength > 0) {
        // read() may give data in smallish chunks, esp. for remote sources.  Hence, this loop.
        val bytesRead = inputStream.read(rawInput, rawLength, remainingLength)
        if (bytesRead <= 0) {
            break
        }
        rawLength += bytesRead
        remainingLength -= bytesRead
    }
    inputStream.reset()

    // TODO: Improve charset detection
    val charsets = listOf("UTF-8", "ISO-8859-1", "ISO-8859-7").map { Charset.forName(it) }

    for (charset in charsets) {
        try {
            val decodedBytes = charset.decode(ByteBuffer.wrap(rawInput))
            if (!decodedBytes.contains("�")) {
                return charset
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return StandardCharsets.UTF_8
        }
    }

    return StandardCharsets.UTF_8
}

fun Context.convertToUTF8(inputUri: Uri, inputStreamReader: InputStreamReader): Uri {
    if (!StandardCharsets.UTF_8.displayName().equals(inputStreamReader.encoding)) {
        val fileName = getFilenameFromUri(inputUri)
        val file = File(cacheDir, fileName)
        val bufferedReader = BufferedReader(inputStreamReader)
        val bufferedWriter = BufferedWriter(FileWriter(file))

        val buffer = CharArray(512)
        var bytesRead: Int

        while (bufferedReader.read(buffer).also { bytesRead = it } != -1) {
            bufferedWriter.write(buffer, 0, bytesRead)
        }

        bufferedWriter.close()
        bufferedReader.close()

        return Uri.fromFile(file)
    }
    return inputUri
}

fun Context.clearCache() {
    try {
        cacheDir.listFiles()?.onEach {
            if (it.isFile) it.delete()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun Context.deleteFile(uri: Uri, intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>) {
    try {
        contentResolver.delete(uri, null, null)
    } catch (e: SecurityException) {
        val intentSender = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                MediaStore.createDeleteRequest(contentResolver, listOf(uri)).intentSender
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                val recoverableSecurityException = e as? RecoverableSecurityException
                recoverableSecurityException?.userAction?.actionIntent?.intentSender
            }

            else -> null
        }

        intentSender?.let {
            intentSenderLauncher.launch(IntentSenderRequest.Builder(it).build())
        }
    }
}
