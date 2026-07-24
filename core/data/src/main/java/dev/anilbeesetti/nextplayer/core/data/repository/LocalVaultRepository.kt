package dev.anilbeesetti.nextplayer.core.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.Logger
import dev.anilbeesetti.nextplayer.core.common.Utils
import dev.anilbeesetti.nextplayer.core.data.mappers.toAudioStreamInfo
import dev.anilbeesetti.nextplayer.core.data.mappers.toSubtitleStreamInfo
import dev.anilbeesetti.nextplayer.core.data.mappers.toVideoStreamInfo
import dev.anilbeesetti.nextplayer.core.database.dao.HiddenVideoDao
import dev.anilbeesetti.nextplayer.core.database.entities.HiddenVideoEntity
import dev.anilbeesetti.nextplayer.core.media.services.MediaOperationsService
import dev.anilbeesetti.nextplayer.core.model.MediaInfo
import dev.anilbeesetti.nextplayer.core.model.Video
import io.github.anilbeesetti.nextlib.mediainfo.MediaInfoBuilder
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class LocalVaultRepository @Inject constructor(
    private val hiddenVideoDao: HiddenVideoDao,
    private val mediaOperationsService: MediaOperationsService,
    @ApplicationContext private val context: Context,
) : VaultRepository {

    private val vaultMutationMutex = Mutex()
    private val pendingVaultPaths = MutableStateFlow<Set<String>>(emptySet())

    private val vaultDir: File by lazy {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        File(base, VAULT_DIR_NAME).apply { if (!exists()) mkdirs() }
    }

    override fun observeHiddenVideos(): Flow<List<Video>> {
        return combine(hiddenVideoDao.getAll(), pendingVaultPaths) { entities, pendingPaths ->
            entities
                .filterNot { it.vaultPath in pendingPaths }
                .filter { File(it.vaultPath).exists() }
                .map { it.toVideo() }
        }
    }

    override suspend fun hideVideos(videos: List<Video>) = vaultMutationMutex.withLock {
        hideVideosLocked(videos)
    }

    private suspend fun hideVideosLocked(videos: List<Video>) {
        val reservations = reserveVideos(videos)
        if (reservations.isEmpty()) return

        try {
            val moveOutcome = moveReservedVideos(reservations)
            reconcileReservations(reservations, moveOutcome)
            moveOutcome.rethrowCancellation()
        } finally {
            revealReservations(reservations.map { it.destination.absolutePath })
        }
    }

    private suspend fun reserveVideos(videos: List<Video>): List<HideReservation> {
        val attemptedVaultPaths = mutableListOf<String>()
        return try {
            videos.mapNotNull { video ->
                reserveVideo(video, attemptedVaultPaths)
            }
        } catch (e: CancellationException) {
            deleteReservationsByVaultPath(attemptedVaultPaths)
            revealReservations(attemptedVaultPaths)
            throw e
        }
    }

    private suspend fun reserveVideo(
        video: Video,
        attemptedVaultPaths: MutableList<String>,
    ): HideReservation? {
        val destination = createVaultDestination(video.nameWithExtension)
        attemptedVaultPaths += destination.absolutePath
        val sourceUri = video.uriString.toUri()
        val entity = video.toHiddenVideoEntity(destination)
        pendingVaultPaths.update { it + destination.absolutePath }
        val rowId = try {
            hiddenVideoDao.insert(entity)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            deleteReservationsByVaultPath(listOf(destination.absolutePath))
            revealReservations(listOf(destination.absolutePath))
            return null
        }
        return HideReservation(
            rowId = rowId,
            sourceUri = sourceUri,
            destination = destination,
        )
    }

    private fun Video.toHiddenVideoEntity(destination: File): HiddenVideoEntity {
        return HiddenVideoEntity(
            vaultPath = destination.absolutePath,
            originalPath = path,
            displayName = nameWithExtension,
            duration = duration,
            size = size,
            width = width,
            height = height,
            hiddenAt = System.currentTimeMillis(),
        )
    }

    private data class HideReservation(
        val rowId: Long,
        val sourceUri: Uri,
        val destination: File,
    )

    private sealed interface MoveOutcome {
        data class Completed(val movedFiles: Map<Uri, File?>) : MoveOutcome
        data object Failed : MoveOutcome
        data class Cancelled(val exception: CancellationException) : MoveOutcome
    }

    private suspend fun moveReservedVideos(
        reservations: List<HideReservation>,
    ): MoveOutcome {
        return try {
            MoveOutcome.Completed(
                mediaOperationsService.moveMedia(
                    reservations.associate { it.sourceUri to it.destination },
                ),
            )
        } catch (e: CancellationException) {
            MoveOutcome.Cancelled(e)
        } catch (e: Exception) {
            MoveOutcome.Failed
        }
    }

    private fun createVaultDestination(displayName: String): File {
        val extension = File(displayName).extension.takeIf { it.isNotBlank() }
        val suffix = extension?.let { ".$it" }.orEmpty()
        return generateSequence { File(vaultDir, "${UUID.randomUUID()}$suffix") }
            .first { !it.exists() }
    }

    private suspend fun reconcileReservations(
        reservations: List<HideReservation>,
        moveOutcome: MoveOutcome,
    ) {
        val failedRowIds = reservations.mapNotNull { reservation ->
            reservation.rowId.takeUnless { moveOutcome.wasCommitted(reservation) }
        }
        if (failedRowIds.isEmpty()) return
        withContext(NonCancellable) {
            runCatching { hiddenVideoDao.deleteByIds(failedRowIds) }
                .onFailure { logCleanupFailure("delete failed hide reservations", it) }
        }
    }

    private fun MoveOutcome.wasCommitted(reservation: HideReservation): Boolean {
        return when (this) {
            is MoveOutcome.Completed -> {
                movedFiles[reservation.sourceUri] == reservation.destination &&
                    reservation.destination.exists()
            }
            MoveOutcome.Failed, is MoveOutcome.Cancelled -> reservation.destination.exists()
        }
    }

    private fun MoveOutcome.rethrowCancellation() {
        if (this is MoveOutcome.Cancelled) throw exception
    }

    private suspend fun deleteReservationsByVaultPath(vaultPaths: List<String>) {
        if (vaultPaths.isEmpty()) return
        withContext(NonCancellable) {
            runCatching { hiddenVideoDao.deleteByVaultPaths(vaultPaths) }
                .onFailure { logCleanupFailure("delete reservations by vault path", it) }
        }
    }

    private fun revealReservations(vaultPaths: List<String>) {
        pendingVaultPaths.update { it - vaultPaths.toSet() }
    }

    private fun logCleanupFailure(operation: String, throwable: Throwable) {
        Logger.logError(TAG, "Failed to $operation: ${throwable.stackTraceToString()}")
    }

    override suspend fun unhideVideos(videos: List<Video>): UnhideResult = vaultMutationMutex.withLock {
        val restored = withContext(Dispatchers.IO) {
            entitiesFor(videos).mapNotNull { entity ->
                restoreToMediaStore(entity)?.let { relocated -> entity.id to relocated }
            }
        }
        if (restored.isNotEmpty()) hiddenVideoDao.deleteByIds(restored.map { it.first })
        UnhideResult(relocatedCount = restored.count { (_, relocated) -> relocated })
    }

    /**
     * Recreates the video in shared storage via a MediaStore insert, streams the vault file
     * into it, then deletes the vault copy. Returns `null` on failure, or whether the file had to
     * be relocated to the fallback directory (`true`) rather than its original folder (`false`).
     *
     * MediaStore is used for every API level because scoped storage (API 29+) forbids writing
     * directly into shared storage. On older versions the exact original path is restored via the
     * `DATA` column; on newer ones the folder is expressed as a `RELATIVE_PATH`.
     */
    private fun restoreToMediaStore(entity: HiddenVideoEntity): Boolean? {
        val vaultFile = File(entity.vaultPath)
        if (!vaultFile.exists()) return null

        val resolver = context.contentResolver
        val inserted = insertPendingItem(entity) ?: return null

        return try {
            resolver.openOutputStream(inserted.uri)?.use { output ->
                vaultFile.inputStream().use { it.copyTo(output) }
            } ?: error("Unable to open output stream for ${inserted.uri}")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                    inserted.uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                )
            }
            vaultFile.delete()
            inserted.relocated
        } catch (e: Exception) {
            resolver.delete(inserted.uri, null, null) // roll back the partially-written entry
            null
        }
    }

    /**
     * Inserts an empty (pending) MediaStore row to receive the restored file.
     *
     * On API 29+ it tries the original folder first, then falls back to [DEFAULT_RESTORE_DIR] when
     * MediaStore rejects it — which happens for custom top-level folders (e.g. `ls/`) or files that
     * were at storage root, since scoped storage only permits the standard media directories.
     * Pre-scoped-storage, the exact original path is restored via the `DATA` column.
     */
    private fun insertPendingItem(entity: HiddenVideoEntity): InsertedItem? {
        val resolver = context.contentResolver
        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(File(entity.vaultPath).extension.lowercase()) ?: "video/*"

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, entity.displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.DATA, entity.originalPath)
            }
            val uri = runCatching { resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) }.getOrNull()
            return uri?.let { InsertedItem(it, relocated = false) }
        }

        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val originalDir = relativePathFor(entity.originalPath)
        val candidateDirs = listOfNotNull(originalDir, DEFAULT_RESTORE_DIR).distinct()

        return candidateDirs.firstNotNullOfOrNull { relativePath ->
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, entity.displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            runCatching { resolver.insert(collection, values) }.getOrNull()
                ?.let { InsertedItem(it, relocated = relativePath != originalDir) }
        }
    }

    private data class InsertedItem(val uri: Uri, val relocated: Boolean)

    /**
     * The MediaStore `RELATIVE_PATH` (e.g. `DCIM/Camera/`) for [originalPath], or `null` when it
     * isn't under primary shared storage (root-level files, or a secondary volume).
     */
    private fun relativePathFor(originalPath: String): String? {
        val root = Environment.getExternalStorageDirectory().path
        val parent = File(originalPath).parent ?: return null
        if (!parent.startsWith(root)) return null
        return parent.removePrefix(root).trim('/').takeIf { it.isNotEmpty() }?.let { "$it/" }
    }

    override suspend fun deleteHiddenVideos(videos: List<Video>): Unit = vaultMutationMutex.withLock {
        val entities = entitiesFor(videos)
        if (entities.isEmpty()) return@withLock
        entities.forEach { File(it.vaultPath).delete() }
        hiddenVideoDao.deleteByIds(entities.map { it.id })
    }

    override suspend fun getHiddenVideoInfo(id: Long): MediaInfo? {
        val entity = hiddenVideoDao.getById(id) ?: return null
        val video = entity.toVideo()
        val mediaInfo = runCatching {
            MediaInfoBuilder().from(context = context, uri = Uri.fromFile(File(entity.vaultPath))).build()
        }.getOrNull()

        return MediaInfo(
            video = video.copy(format = mediaInfo?.format),
            videoStream = mediaInfo?.videoStream?.toVideoStreamInfo(),
            audioStreams = mediaInfo?.audioStreams?.map { it.toAudioStreamInfo() } ?: emptyList(),
            subtitleStreams = mediaInfo?.subtitleStreams?.map { it.toSubtitleStreamInfo() } ?: emptyList(),
        ).also { mediaInfo?.release() }
    }

    /** Looks up the vault entities backing the given [videos] by their row id. */
    private suspend fun entitiesFor(videos: List<Video>): List<HiddenVideoEntity> {
        val ids = videos.map { it.id }.toSet()
        return hiddenVideoDao.getAll().first().filter { it.id in ids }
    }

    private fun fileProviderUri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun HiddenVideoEntity.toVideo(): Video {
        val uriString = fileProviderUri(File(vaultPath)).toString()
        return Video(
            id = id,
            path = vaultPath,
            parentPath = File(vaultPath).parent ?: "",
            duration = duration,
            uriString = uriString,
            nameWithExtension = displayName,
            width = width,
            height = height,
            size = size,
            dateModified = hiddenAt,
            formattedDuration = Utils.formatDurationMillis(duration),
            formattedFileSize = Utils.formatFileSize(size),
        )
    }

    companion object {
        private const val TAG = "LocalVaultRepository"
        private const val VAULT_DIR_NAME = "vault"

        /** Fallback folder for videos whose original location MediaStore won't accept. */
        private val DEFAULT_RESTORE_DIR = Environment.DIRECTORY_MOVIES + "/"
    }
}
