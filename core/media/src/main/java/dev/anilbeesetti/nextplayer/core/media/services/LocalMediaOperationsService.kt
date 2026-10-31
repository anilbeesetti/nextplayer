package dev.anilbeesetti.nextplayer.core.media.services

import android.app.Activity
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.BaseColumns
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import dev.anilbeesetti.nextplayer.core.common.extensions.deleteMedia
import dev.anilbeesetti.nextplayer.core.common.extensions.getFilenameFromUri
import dev.anilbeesetti.nextplayer.core.common.extensions.getPath
import dev.anilbeesetti.nextplayer.core.common.extensions.scanPaths
import dev.anilbeesetti.nextplayer.core.common.extensions.updateMedia
import dev.anilbeesetti.nextplayer.core.common.service.SuspendActivityResultLauncher
import dev.anilbeesetti.nextplayer.core.common.service.registerForSuspendActivityResult
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
class LocalMediaOperationsService(private val context: Context) : MediaOperationsService {

    private lateinit var activity: Activity
    private val contentResolver = context.contentResolver
    private var mediaRequestLauncher:
        SuspendActivityResultLauncher<IntentSenderRequest, ActivityResult>? = null

    override fun initialize(activity: ComponentActivity) {
        this.activity = activity
        mediaRequestLauncher = activity.registerForSuspendActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult(),
        )
    }

    override suspend fun deleteMedia(uris: List<Uri>, permanently: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (permanently) deleteMediaR(uris = uris) else trashMediaR(uris = uris)
            } else {
                deleteMediaBelowR(uris = uris)
            }
        }

    override suspend fun restoreMedia(uris: List<Uri>): Boolean = withContext(Dispatchers.IO) {
        return@withContext Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            trashMediaR(
                uris = uris,
                trashed = false,
            )
    }

    override suspend fun renameMedia(uri: Uri, to: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            renameMediaR(
                uri = uri,
                to = to,
            )
        } else {
            renameMediaBelowR(
                uri = uri,
                to = to,
            )
        }
    }

    override suspend fun shareMedia(uris: List<Uri>) {
        val intent = Intent.createChooser(
            Intent().apply {
                type = "video/*"
                action = Intent.ACTION_SEND_MULTIPLE
                putParcelableArrayListExtra(
                    Intent.EXTRA_STREAM,
                    ArrayList(uris),
                )
            },
            null,
        )
        activity.startActivity(intent)
    }

    override suspend fun moveMedia(targets: Map<Uri, File>): Map<Uri, File?> = moveMedia(
        targets = targets,
        onMoveCommitted = null,
    )

    /**
     * The internal callback is a deterministic synchronization seam for commit-boundary tests.
     * Production calls use [moveMedia] and pay no callback cost.
     */
    internal suspend fun moveMedia(
        targets: Map<Uri, File>,
        onMoveCommitted: (suspend (Uri) -> Unit)?,
    ): Map<Uri, File?> {
        val uris = targets.keys.toList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mediaStoreUris = uris.filter { it.authority == MediaStore.AUTHORITY }
            if (mediaStoreUris.isNotEmpty()) {
                val granted = try {
                    requestWriteAccessR(uris = mediaStoreUris)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
                if (!granted) return targets.keys.associateWith { null }
            }
        }

        val callerJob = currentCoroutineContext()[Job]
        return withContext(context = NonCancellable) {
            withContext(Dispatchers.IO) io@{
                val movedFiles = linkedMapOf<Uri, File?>()
                val entries = targets.entries.iterator()
                var moveCommitted = false
                while (entries.hasNext()) {
                    val (uri, destination) = entries.next()
                    val movedFile = try {
                        callerJob?.ensureActive()
                        val sourceFile = context.getPath(uri = uri)?.let { File(it) }
                        if (sourceFile == null) {
                            null
                        } else {
                            destination.parentFile?.mkdirs()
                            moveFileWithoutOverwrite(
                                source = sourceFile,
                                destination = destination,
                            ) { callerJob?.ensureActive() }
                        }
                    } catch (e: CancellationException) {
                        if (!moveCommitted) throw e
                        movedFiles[uri] = null
                        while (entries.hasNext()) movedFiles[entries.next().key] = null
                        return@io movedFiles
                    } catch (e: Exception) {
                        null
                    }
                    movedFiles[uri] = movedFile
                    moveCommitted = moveCommitted || movedFile != null
                    if (movedFile != null) onMoveCommitted?.invoke(uri)
                }
                movedFiles
            }
        }
    }

    /** Copies into an exclusively-created destination before atomically committing source deletion. */
    internal fun moveFileWithoutOverwrite(
        source: File,
        destination: File,
        ensureActive: () -> Unit,
    ): File? {
        if (!source.exists() || !destination.createNewFile()) return null
        val expectedBytes = source.length()

        val copiedBytes = try {
            source.inputStream().use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(size = DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(
                            buffer,
                            0,
                            read,
                        )
                        copied += read
                    }
                    output.fd.sync()
                    ensureActive()
                    copied
                }
            }
        } catch (e: CancellationException) {
            destination.delete()
            throw e
        } catch (e: Exception) {
            destination.delete()
            return null
        }

        // Cancellation before this point rolls back the destination. From here, the move is committed.
        if (copiedBytes != expectedBytes || !source.delete()) {
            destination.delete()
            return null
        }
        return destination
    }

    override fun transferMedia(
        uris: List<Uri>,
        folderUri: Uri,
        mode: TransferMode,
    ): Flow<TransferEvent> = flow {
        val destinationDir = folderUri.toTreeDocumentUri()

        // Moving a file into the folder it already lives in is a no-op, so keep those aside.
        val (sameFolder, sources) = if (mode == TransferMode.MOVE) {
            uris.partition {
                isInFolder(
                    uri = it,
                    folderDocUri = destinationDir,
                )
            }
        } else {
            emptyList<Uri>() to uris
        }

        if (mode == TransferMode.MOVE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mediaUris = sources.filter { it.authority == MediaStore.AUTHORITY }
            if (!requestWriteAccessR(uris = mediaUris)) {
                emit(
                    TransferEvent.Completed(
                        result = TransferResult(
                            succeeded = 0,
                            failed = sources.size,
                            sameFolderSkipped = sameFolder.size,
                        ),
                    ),
                )
                return@flow
            }
        }

        val sizes = sources.map { sizeOf(uri = it) }
        val overallTotal = sizes.sum()

        val createdPaths = mutableListOf<String>()
        var originalsNotDeleted = false
        var overallCopied = 0L
        var succeeded = 0

        sources.forEachIndexed { index, source ->
            val name = context.getFilenameFromUri(uri = source).takeIf { it.isNotBlank() }
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

            emit(TransferEvent.Progress(progress = progress(copied = 0)))
            if (name == null) return@forEachIndexed

            val copied =
                copyToDocument(
                    source = source,
                    destinationDir = destinationDir,
                    name = name,
                    expectedSize = currentTotal,
                ) { copiedSoFar ->
                    emit(TransferEvent.Progress(progress = progress(copied = copiedSoFar)))
                } ?: return@forEachIndexed

            succeeded++
            overallCopied += copied.bytes
            context.getPath(copied.uri)?.let { createdPaths.add(element = it) }
            if (mode == TransferMode.MOVE) {
                // Finish each committed move even if cancellation arrives after the copy.
                val deleted = withContext(NonCancellable) {
                    contentResolver.deleteMedia(uri = source)
                }
                originalsNotDeleted = originalsNotDeleted || !deleted
            }
        }

        if (createdPaths.isNotEmpty()) context.scanPaths(paths = createdPaths)

        emit(
            TransferEvent.Completed(
                result = TransferResult(
                    succeeded = succeeded,
                    failed = sources.size - succeeded,
                    sameFolderSkipped = sameFolder.size,
                    originalsNotDeleted = originalsNotDeleted,
                ),
            ),
        )
    }.flowOn(Dispatchers.IO)

    private fun isInFolder(uri: Uri, folderDocUri: Uri): Boolean {
        val folderPath =
            runCatching { context.getPath(uri = folderDocUri) }.getOrNull() ?: return false
        val filePath = runCatching { context.getPath(uri = uri) }.getOrNull() ?: return false
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
            DocumentsContract.createDocument(
                contentResolver,
                destinationDir,
                mimeType,
                name,
            )
        }.getOrNull() ?: return null

        return try {
            val bytes = contentResolver.openInputStream(source)?.use { input ->
                contentResolver.openOutputStream(destUri)?.use { output ->
                    copyStream(
                        input = input,
                        output = output,
                        expectedTotal = expectedSize,
                        onCopied = onProgress,
                    )
                } ?: error(message = "Unable to open output stream for $destUri")
            } ?: error(message = "Unable to open input stream for $source")
            check(value = expectedSize <= 0 || bytes == expectedSize) {
                "Incomplete copy of $source"
            }
            CopyResult(
                uri = destUri,
                bytes = bytes,
            )
        } catch (e: CancellationException) {
            deleteDocumentQuietly(uri = destUri)
            throw e
        } catch (e: Exception) {
            deleteDocumentQuietly(uri = destUri)
            null
        }
    }

    private fun deleteDocumentQuietly(uri: Uri) {
        runCatching {
            DocumentsContract.deleteDocument(
                contentResolver,
                uri,
            )
        }
    }

    private fun Uri.toTreeDocumentUri(): Uri = DocumentsContract.buildDocumentUriUsingTree(
        this,
        DocumentsContract.getTreeDocumentId(this),
    )

    private fun sizeOf(uri: Uri): Long = runCatching {
        contentResolver.openFileDescriptor(
            uri,
            "r",
        )?.use {
            it.statSize.coerceAtLeast(minimumValue = 0)
        }
    }.getOrNull() ?: 0L

    private suspend fun copyStream(
        input: InputStream,
        output: OutputStream,
        expectedTotal: Long,
        onCopied: suspend (Long) -> Unit,
    ): Long {
        val buffer = ByteArray(size = DEFAULT_BUFFER_SIZE)
        val reportThreshold = maxOf(
            a = expectedTotal / 100,
            b = 64L * 1024,
        )
        var copied = 0L
        var lastReported = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(
                buffer,
                0,
                read,
            )
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
    private suspend fun requestWriteAccessR(uris: List<Uri>): Boolean = runMediaWriteRequests(
        uris = uris.filter {
            context.checkUriPermission(
                it,
                Process.myPid(),
                Process.myUid(),
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            ) != PackageManager.PERMISSION_GRANTED
        },
        itemExists = ::mediaExists,
        request = ::requestWriteR,
    )

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun requestWriteR(uris: List<Uri>): Boolean = requestMediaAccess(
        pendingIntent = MediaStore.createWriteRequest(
            contentResolver,
            uris,
        ),
    )

    private suspend fun requestMediaAccess(pendingIntent: PendingIntent): Boolean =
        mediaRequestLauncher?.launch(
            input = IntentSenderRequest.Builder(pendingIntent = pendingIntent).build(),
        )?.resultCode == Activity.RESULT_OK

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun deleteMediaR(uris: List<Uri>): Boolean = runMediaRequests(
        items = uris,
        itemExists = ::mediaExists,
        request = ::requestDeleteR,
    )

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun trashMediaR(uris: List<Uri>, trashed: Boolean = true): Boolean =
        runMediaRequests(
            items = uris,
            itemExists = ::mediaExists,
            request = {
                requestTrashR(
                    uris = it,
                    trashed = trashed,
                )
            },
        )

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun requestTrashR(uris: List<Uri>, trashed: Boolean): Boolean =
        requestMediaAccess(
            pendingIntent = MediaStore.createTrashRequest(
                contentResolver,
                uris,
                trashed,
            ),
        )

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun requestDeleteR(uris: List<Uri>): Boolean = requestMediaAccess(
        pendingIntent = MediaStore.createDeleteRequest(
            contentResolver,
            uris,
        ),
    )

    private fun mediaExists(uri: Uri): Boolean = runCatching {
        contentResolver.query(
            uri,
            arrayOf(BaseColumns._ID),
            null,
            null,
            null,
        )?.use { cursor -> cursor.moveToFirst() } == true
    }.getOrDefault(defaultValue = false)

    private suspend fun deleteMediaBelowR(uris: List<Uri>): Boolean = uris.map { uri ->
        contentResolver.deleteMedia(uri = uri)
    }.all { it }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun renameMediaR(uri: Uri, to: String): Boolean =
        requestWriteAccessR(uris = listOf(element = uri)) && contentResolver.updateMedia(
            uri = uri,
            contentValues = ContentValues().apply {
                put(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    to,
                )
            },
        )

    private suspend fun renameMediaBelowR(uri: Uri, to: String): Boolean = runCatching {
        val oldFile = context.getPath(uri = uri)?.let { File(it) } ?: throw Error()
        val newFile = File(
            oldFile.parentFile,
            to,
        )
        oldFile.renameTo(newFile).also { success ->
            if (success) {
                contentResolver.updateMedia(
                    uri = uri,
                    contentValues = ContentValues().apply {
                        put(
                            MediaStore.Files.FileColumns.DISPLAY_NAME,
                            to,
                        )
                        put(
                            MediaStore.Files.FileColumns.TITLE,
                            to,
                        )
                        put(
                            MediaStore.Files.FileColumns.DATA,
                            newFile.path,
                        )
                    },
                )
            }
        }
    }.getOrNull() ?: false
}
