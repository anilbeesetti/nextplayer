package dev.anilbeesetti.nextplayer.core.media.services

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.extensions.deleteMedia
import dev.anilbeesetti.nextplayer.core.common.extensions.getPath
import dev.anilbeesetti.nextplayer.core.common.extensions.updateMedia
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class LocalMediaService @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaService {

    private lateinit var activity: Activity
    private val contentResolver = context.contentResolver
    private var resultOkCallback: () -> Unit = {}
    private var resultCancelledCallback: () -> Unit = {}
    private var mediaRequestLauncher: ActivityResultLauncher<IntentSenderRequest>? = null

    override fun initialize(activity: ComponentActivity) {
        this.activity = activity
        mediaRequestLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            when (result.resultCode) {
                Activity.RESULT_OK -> resultOkCallback()
                Activity.RESULT_CANCELED -> resultCancelledCallback()
            }
        }
    }

    override suspend fun deleteMedia(uris: List<Uri>): Boolean = withContext(Dispatchers.IO) {
        return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            deleteMediaR(uris)
        } else {
            deleteMediaBelowR(uris)
        }
    }

    override suspend fun renameMedia(uri: Uri, to: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            renameMediaR(uri, to)
        } else {
            renameMediaBelowR(uri, to)
        }
    }

    override suspend fun shareMedia(uris: List<Uri>) {
        val intent = Intent.createChooser(
            Intent().apply {
                type = "video/*"
                action = Intent.ACTION_SEND_MULTIPLE
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            },
            null,
        )
        activity.startActivity(intent)
    }

    override suspend fun hideVideos(uris: List<Uri>): Boolean = withContext(Dispatchers.IO) {
        return@withContext runCatching {
            // Vault folder inside app-private external storage.
            // Gets deleted automatically if app is uninstalled.
            // .nomedia tells Android gallery apps to ignore this folder.
            val vaultDir = File(context.getExternalFilesDir(null), ".vault")
            if (!vaultDir.exists()) vaultDir.mkdirs()
            val nomediaFile = File(vaultDir, ".nomedia")
            if (!nomediaFile.exists()) nomediaFile.createNewFile()

            uris.all { uri ->
                // Get filename using OpenableColumns — works on ALL Android versions including 10+
                val filename = getFilenameFromUri(uri) ?: return@all false

                // Handle duplicate filenames in vault
                var destFile = File(vaultDir, filename)
                if (destFile.exists()) {
                    val name = filename.substringBeforeLast(".")
                    val ext = filename.substringAfterLast(".", "")
                    destFile = File(
                        vaultDir,
                        "${name}_${System.currentTimeMillis()}${if (ext.isNotEmpty()) ".$ext" else ""}",
                    )
                }

                // Copy bytes via ContentResolver — no file path needed, works on Android 10+
                val inputStream = contentResolver.openInputStream(uri) ?: return@all false
                inputStream.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // Delete original from MediaStore (removes from library + deletes the file)
                contentResolver.delete(uri, null, null)

                true
            }
        }.getOrElse { it.printStackTrace(); false }
    }

    /**
     * Gets display filename from a content URI using OpenableColumns.
     * This works on all Android versions including Android 10+ where DATA column is deprecated.
     */
    private fun getFilenameFromUri(uri: Uri): String? {
        try {
            contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Fallback
        return uri.lastPathSegment?.substringAfterLast("/")
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun launchWriteRequest(
        uris: List<Uri>,
        onResultCanceled: () -> Unit = {},
        onResultOk: () -> Unit = {},
    ) {
        resultOkCallback = onResultOk
        resultCancelledCallback = onResultCanceled
        MediaStore.createWriteRequest(contentResolver, uris).also { intent ->
            mediaRequestLauncher?.launch(IntentSenderRequest.Builder(intent).build())
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun launchDeleteRequest(
        uris: List<Uri>,
        onResultCanceled: () -> Unit = {},
        onResultOk: () -> Unit = {},
    ) {
        resultOkCallback = onResultOk
        resultCancelledCallback = onResultCanceled
        MediaStore.createDeleteRequest(contentResolver, uris).also { intent ->
            mediaRequestLauncher?.launch(IntentSenderRequest.Builder(intent).build())
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun deleteMediaR(uris: List<Uri>): Boolean = suspendCancellableCoroutine { continuation ->
        launchDeleteRequest(
            uris = uris,
            onResultOk = { continuation.resume(true) },
            onResultCanceled = { continuation.resume(false) },
        )
    }

    private suspend fun deleteMediaBelowR(uris: List<Uri>): Boolean {
        return uris.map { uri ->
            contentResolver.deleteMedia(uri)
        }.all { it }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun renameMediaR(uri: Uri, to: String): Boolean = suspendCancellableCoroutine { continuation ->
        val scope = CoroutineScope(Dispatchers.Default)
        launchWriteRequest(
            uris = listOf(uri),
            onResultOk = {
                scope.launch {
                    val result = contentResolver.updateMedia(
                        uri = uri,
                        contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, to)
                        },
                    )
                    continuation.resume(result)
                }
            },
            onResultCanceled = { continuation.resume(false) },
        )
        continuation.invokeOnCancellation { scope.cancel() }
    }

    private suspend fun renameMediaBelowR(uri: Uri, to: String): Boolean {
        return runCatching {
            val oldFile = context.getPath(uri)?.let { File(it) } ?: throw Error()
            val newFile = File(oldFile.parentFile, to)
            oldFile.renameTo(newFile).also { success ->
                if (success) {
                    contentResolver.updateMedia(
                        uri = uri,
                        contentValues = ContentValues().apply {
                            put(MediaStore.Files.FileColumns.DISPLAY_NAME, to)
                            put(MediaStore.Files.FileColumns.TITLE, to)
                            put(MediaStore.Files.FileColumns.DATA, newFile.path)
                        },
                    )
                }
            }
        }.getOrNull() ?: false
    }
}
