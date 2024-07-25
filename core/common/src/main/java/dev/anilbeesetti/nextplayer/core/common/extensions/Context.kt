package dev.anilbeesetti.nextplayer.core.common.extensions

import android.app.UiModeManager
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.Cursor
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.util.TypedValue
import androidx.core.text.isDigitsOnly
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mozilla.universalchardet.UniversalDetector

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
                            docId.toLong(),
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
                    split[1],
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
    selectionArgs: Array<String>? = null,
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
        OpenableColumns.DISPLAY_NAME,
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
            null,
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

suspend fun Context.scanPaths(paths: List<String>): Boolean = suspendCoroutine { continuation ->
    try {
        MediaScannerConnection.scanFile(
            this@scanPaths,
            paths.toTypedArray(),
            arrayOf("video/*"),
        ) { path, uri ->
            Log.d("ScanPath", "scanPaths: path=$path, uri=$uri")
            continuation.resumeWith(Result.success(true))
        }
    } catch (e: Exception) {
        continuation.resumeWith(Result.failure(e))
    }
}

suspend fun Context.scanPath(file: File): Boolean {
    return if (file.isDirectory) {
        file.listFiles()?.all { scanPath(it) } ?: true
    } else {
        scanPaths(listOf(file.path))
    }
}

suspend fun Context.scanStorage(
    storagePath: String? = Environment.getExternalStorageDirectory()?.path,
): Boolean = withContext(Dispatchers.IO) {
    if (storagePath != null) {
        return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scanPaths(listOf(storagePath))
        } else {
            scanPath(File(storagePath))
        }
    } else {
        false
    }
}

suspend fun Context.convertToUTF8(uri: Uri, charset: Charset? = null): Uri = withContext(Dispatchers.IO) {
    try {
        when {
            uri.scheme?.let { it in listOf("http", "https", "ftp") } == true -> {
                val url = URL(uri.toString())
                val detectedCharset = charset ?: detectCharset(url)
                if (detectedCharset == StandardCharsets.UTF_8) {
                    uri
                } else {
                    convertNetworkUriToUTF8(url = url, sourceCharset = detectedCharset)
                }
            }
            else -> {
                val detectedCharset = charset ?: detectCharset(uri = uri, context = this@convertToUTF8)
                if (detectedCharset == StandardCharsets.UTF_8) {
                    uri
                } else {
                    convertLocalUriToUTF8(uri = uri, sourceCharset = detectedCharset)
                }
            }
        }
    } catch (exception: Exception) {
        exception.printStackTrace()
        uri
    }
}

private fun detectCharset(uri: Uri, context: Context): Charset {
    return context.contentResolver.openInputStream(uri)?.use { inputStream ->
        detectCharsetFromStream(inputStream)
    } ?: StandardCharsets.UTF_8
}

private fun detectCharset(url: URL): Charset {
    return url.openStream().use { inputStream ->
        detectCharsetFromStream(inputStream)
    }
}

private fun detectCharsetFromStream(inputStream: InputStream): Charset {
    return BufferedInputStream(inputStream).use { bufferedStream ->
        val data = bufferedStream.readBytes()
        UniversalDetector(null).run {
            handleData(data, 0, data.size)
            dataEnd()
            Charset.forName(detectedCharset ?: StandardCharsets.UTF_8.name())
        }
    }
}

private fun Context.convertLocalUriToUTF8(uri: Uri, sourceCharset: Charset): Uri {
    val fileName = getFilenameFromUri(uri)
    val file = File(subtitleCacheDir, fileName)

    contentResolver.openInputStream(uri)?.use { inputStream ->
        inputStream.reader(sourceCharset).buffered().use { reader ->
            file.outputStream().writer(StandardCharsets.UTF_8).buffered().use { writer ->
                reader.copyTo(writer)
            }
        }
    }

    return Uri.fromFile(file)
}

private fun Context.convertNetworkUriToUTF8(url: URL, sourceCharset: Charset): Uri {
    val fileName = url.path.substringAfterLast('/')
    val file = File(subtitleCacheDir, fileName)

    url.openStream().use { inputStream ->
        inputStream.reader(sourceCharset).buffered().use { reader ->
            file.outputStream().writer(StandardCharsets.UTF_8).buffered().use { writer ->
                reader.copyTo(writer)
            }
        }
    }

    return Uri.fromFile(file)
}

fun Context.isDeviceTvBox(): Boolean {
    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
        return true
    }

    // Fire tv
    if (packageManager.hasSystemFeature("amazon.hardware.fire_tv")) {
        return true
    }

    // Missing Files app (DocumentsUI) means box (some boxes still have non functional app or stub)
    if (!hasStorageAccessFrameworkChooser()) {
        return true
    }

    if (Build.VERSION.SDK_INT < 30) {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
            return true
        }

        if (packageManager.hasSystemFeature("android.hardware.hdmi.cec")) {
            return true
        }

        if (Build.MANUFACTURER.equals("zidoo", ignoreCase = true)) {
            return true
        }
    }
    return false
}

fun Context.hasStorageAccessFrameworkChooser(): Boolean {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
    intent.addCategory(Intent.CATEGORY_OPENABLE)
    intent.type = "video/*"
    return intent.resolveActivity(packageManager) != null
}

fun Context.pxToDp(px: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, px, resources.displayMetrics)

fun Context.dpToPx(dp: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)

val Context.subtitleCacheDir: File
    get() {
        val dir = File(cacheDir, "subtitles")
        if (!dir.exists()) dir.mkdir()
        return dir
    }

val Context.thumbnailCacheDir: File
    get() {
        val dir = File(cacheDir, "thumbnails")
        if (!dir.exists()) dir.mkdir()
        return dir
    }

suspend fun ContentResolver.updateMedia(
    uri: Uri,
    contentValues: ContentValues,
): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
        update(
            uri,
            contentValues,
            null,
            null,
        ) > 0
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

suspend fun ContentResolver.deleteMedia(
    uri: Uri,
): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
        delete(uri, null, null) > 0
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
