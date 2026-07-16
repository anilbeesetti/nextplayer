package dev.anilbeesetti.nextplayer.core.media.services

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.BaseColumns
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
        val destinationDir = folderUri.toTreeDocumentUri()

        // Moving a file into the folder it already lives in is a no-op, so keep those aside.
        val (sameFolder, sources) = if (mode == TransferMode.MOVE) {
            uris.partition { isInFolder(it, destinationDir) }
        } else {
            emptyList<Uri>() to uris
        }

        val sizes = sources.map { sizeOf(it) }
        val overallTotal = sizes.sum()

        val createdPaths = mutableListOf<String>()
        val movedSources = mutableListOf<Uri>()
        var overallCopied = 0L
        var succeeded = 0

        sources.forEachIndexed { index, source ->
            val name = context.getFilenameFromUri(source).takeIf { it.isNotBlank() }
            val currentTotal = sizes[index]

            fun progress(copied: Long) = TransferProgress(
                totalFiles = sources.size,
                currentIndex = index,
                currentName = name,
                currentBytesCopied = copied,
                currentBytesTotal = currentTotal,
                overallBytesCopied = overallCopied + copied,
                overallBytesTotal = overallTotal,
            )

            emit(TransferEvent.Progress(progress(0)))
            if (name == null) return@forEachIndexed

            val copied = copyToDocument(source, destinationDir, name, currentTotal) { copiedSoFar ->
                emit(TransferEvent.Progress(progress(copiedSoFar)))
            } ?: return@forEachIndexed

            succeeded++
            overallCopied += copied.bytes
            context.getPath(copied.uri)?.let { createdPaths.add(it) }
            if (mode == TransferMode.MOVE) movedSources.add(source)
        }

        if (createdPaths.isNotEmpty()) context.scanPaths(createdPaths)

        // The copies are already in place, so a failed deletion still counts as a move;
        // the originals are just left behind, which we report separately.
        val originalsNotDeleted = mode == TransferMode.MOVE &&
            movedSources.isNotEmpty() &&
            !deleteMedia(movedSources)

        emit(
            TransferEvent.Completed(
                TransferResult(
                    succeeded = succeeded,
                    failed = sources.size - succeeded,
                    sameFolderSkipped = sameFolder.size,
                    originalsNotDeleted = originalsNotDeleted,
                ),
            ),
        )
    }.flowOn(Dispatchers.IO)

    private fun isInFolder(uri: Uri, folderDocUri: Uri): Boolean {
        val folderPath = runCatching { context.getPath(folderDocUri) }.getOrNull() ?: return false
        val filePath = runCatching { context.getPath(uri) }.getOrNull() ?: return false
        return File(filePath).parent == folderPath
    }

    private data class CopyResult(val uri: Uri, val bytes: Long)

    private suspend fun copyToDocument(
        source: Uri,
        destinationDir: Uri,
        name: String,
        expectedSize: Long,
        onProgress: suspend (Long) -> Unit,
    ): CopyResult? {
        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(File(name).extension.lowercase()) ?: "video/*"
        val destUri = runCatching {
            DocumentsContract.createDocument(contentResolver, destinationDir, mimeType, name)
        }.getOrNull() ?: return null

        return try {
            val bytes = contentResolver.openInputStream(source)?.use { input ->
                contentResolver.openOutputStream(destUri)?.use { output ->
                    copyStream(input, output, expectedSize, onProgress)
                } ?: error("Unable to open output stream for $destUri")
            } ?: error("Unable to open input stream for $source")
            CopyResult(destUri, bytes)
        } catch (e: CancellationException) {
            deleteDocumentQuietly(destUri)
            throw e
        } catch (e: Exception) {
            deleteDocumentQuietly(destUri)
            null
        }
    }

    private fun deleteDocumentQuietly(uri: Uri) {
        runCatching { DocumentsContract.deleteDocument(contentResolver, uri) }
    }

    private fun Uri.toTreeDocumentUri(): Uri = DocumentsContract.buildDocumentUriUsingTree(
        this,
        DocumentsContract.getTreeDocumentId(this),
    )

    private fun sizeOf(uri: Uri): Long = runCatching {
        contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize.coerceAtLeast(0) }
    }.getOrNull() ?: 0L

    private suspend fun copyStream(
        input: InputStream,
        output: OutputStream,
        expectedTotal: Long,
        onCopied: suspend (Long) -> Unit,
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val reportThreshold = maxOf(expectedTotal / 100, 64L * 1024)
        var copied = 0L
        var lastReported = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            copied += read
            if (copied - lastReported >= reportThreshold) {
                onCopied(copied)
                lastReported = copied
            }
        }
        output.flush()
        onCopied(copied)
        return copied
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
    private suspend fun deleteMediaR(uris: List<Uri>): Boolean = runMediaRequests(
        items = uris,
        itemExists = ::mediaExists,
        request = ::requestDeleteR,
    )

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun requestDeleteR(uris: List<Uri>): Boolean = suspendCancellableCoroutine { continuation ->
        launchDeleteRequest(
            uris = uris,
            onResultOk = { continuation.resume(true) },
            onResultCanceled = { continuation.resume(false) },
        )
    }

    private fun mediaExists(uri: Uri): Boolean = runCatching {
        contentResolver.query(
            uri,
            arrayOf(BaseColumns._ID),
            null,
            null,
            null,
        )?.use { cursor -> cursor.moveToFirst() } == true
    }.getOrDefault(false)

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
