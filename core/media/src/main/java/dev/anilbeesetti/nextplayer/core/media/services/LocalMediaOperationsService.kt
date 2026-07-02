package dev.anilbeesetti.nextplayer.core.media.services

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.extensions.deleteMedia
import dev.anilbeesetti.nextplayer.core.common.extensions.getFilenameFromUri
import dev.anilbeesetti.nextplayer.core.common.extensions.getPath
import dev.anilbeesetti.nextplayer.core.common.extensions.scanPaths
import dev.anilbeesetti.nextplayer.core.common.extensions.updateMedia
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class LocalMediaOperationsService @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaOperationsService {

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

    override suspend fun moveMedia(uris: List<Uri>, targetDir: File): Map<Uri, File?> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mediaStoreUris = uris.filter { it.authority == MediaStore.AUTHORITY }
            if (mediaStoreUris.isNotEmpty()) {
                val granted = requestWriteAccessR(mediaStoreUris)
                if (!granted) return@withContext uris.associateWith { null }
            }
        }

        uris.associateWith { uri ->
            val sourceFile = context.getPath(uri)?.let { File(it) } ?: return@associateWith null
            val destFile = File(targetDir, sourceFile.name)
            if (sourceFile.renameTo(destFile)) destFile else null
        }
    }

    override fun transferMedia(
        uris: List<Uri>,
        folderUri: Uri,
        mode: TransferMode,
    ): Flow<TransferEvent> = flow {
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(
            folderUri,
            DocumentsContract.getTreeDocumentId(folderUri),
        )

        val total = uris.size
        val sizes = uris.map { sizeOf(it) }
        val overallTotal = sizes.sum()

        val createdPaths = mutableListOf<String>()
        val movedSources = mutableListOf<Uri>()
        var overallCopied = 0L
        var succeeded = 0

        uris.forEachIndexed { index, uri ->
            val name = context.getFilenameFromUri(uri).takeIf { it.isNotBlank() }
            val currentTotal = sizes[index]

            fun progress(currentCopied: Long) = TransferProgress(
                currentIndex = index,
                totalFiles = total,
                currentName = name,
                currentBytesCopied = currentCopied,
                currentBytesTotal = currentTotal,
                overallBytesCopied = overallCopied + currentCopied,
                overallBytesTotal = overallTotal,
            )

            emit(TransferEvent.Progress(progress(0)))
            if (name == null) return@forEachIndexed

            val mimeType = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(File(name).extension.lowercase()) ?: "video/*"

            val destUri = runCatching {
                DocumentsContract.createDocument(contentResolver, parentDocUri, mimeType, name)
            }.getOrNull() ?: return@forEachIndexed

            val copied = try {
                contentResolver.openInputStream(uri)?.use { input ->
                    contentResolver.openOutputStream(destUri)?.use { output ->
                        copyStream(input, output, currentTotal) { copiedBytes ->
                            emit(TransferEvent.Progress(progress(copiedBytes)))
                        }
                    } ?: error("Unable to open output stream for $destUri")
                } ?: error("Unable to open input stream for $uri")
                true
            } catch (e: CancellationException) {
                runCatching { DocumentsContract.deleteDocument(contentResolver, destUri) }
                throw e
            } catch (e: Exception) {
                false
            }

            if (copied) {
                succeeded++
                overallCopied += currentTotal
                context.getPath(destUri)?.let { createdPaths.add(it) }
                if (mode == TransferMode.MOVE) movedSources.add(uri)
            } else {
                runCatching { DocumentsContract.deleteDocument(contentResolver, destUri) }
            }
        }

        // Register the newly created files with MediaStore so they surface in the app.
        if (createdPaths.isNotEmpty()) context.scanPaths(createdPaths)

        // For a move, remove the originals now that their copies are in place.
        if (mode == TransferMode.MOVE && movedSources.isNotEmpty()) {
            deleteMedia(movedSources)
        }

        emit(TransferEvent.Completed(TransferResult(succeeded = succeeded, failed = total - succeeded)))
    }.flowOn(Dispatchers.IO)

    private fun sizeOf(uri: Uri): Long = runCatching {
        contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize.coerceAtLeast(0) }
    }.getOrNull() ?: 0L

    private suspend fun copyStream(
        input: InputStream,
        output: OutputStream,
        expectedTotal: Long,
        onCopied: suspend (Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val step = maxOf(expectedTotal / 100, 64L * 1024)
        var copied = 0L
        var lastReported = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            copied += read
            if (copied - lastReported >= step) {
                onCopied(copied)
                lastReported = copied
            }
        }
        output.flush()
        onCopied(copied)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun requestWriteAccessR(uris: List<Uri>): Boolean = suspendCancellableCoroutine { continuation ->
        launchWriteRequest(
            uris = uris,
            onResultOk = { continuation.resume(true) },
            onResultCanceled = { continuation.resume(false) },
        )
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