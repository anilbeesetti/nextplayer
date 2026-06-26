package dev.anilbeesetti.nextplayer.core.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.Utils
import dev.anilbeesetti.nextplayer.core.common.extensions.getFilenameFromUri
import dev.anilbeesetti.nextplayer.core.common.extensions.getPath
import dev.anilbeesetti.nextplayer.core.common.extensions.scanPaths
import dev.anilbeesetti.nextplayer.core.data.mappers.toAudioStreamInfo
import dev.anilbeesetti.nextplayer.core.data.mappers.toSubtitleStreamInfo
import dev.anilbeesetti.nextplayer.core.data.mappers.toVideoStreamInfo
import dev.anilbeesetti.nextplayer.core.database.dao.HiddenVideoDao
import dev.anilbeesetti.nextplayer.core.database.entities.HiddenVideoEntity
import dev.anilbeesetti.nextplayer.core.model.MediaInfo
import dev.anilbeesetti.nextplayer.core.model.Video
import io.github.anilbeesetti.nextlib.mediainfo.MediaInfoBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class LocalVaultRepository @Inject constructor(
    private val hiddenVideoDao: HiddenVideoDao,
    @ApplicationContext private val context: Context,
) : VaultRepository {

    private val vaultDir: File by lazy {
        val externalDir = context.getExternalFilesDir(null)
        val base = externalDir ?: context.filesDir
        File(base, VAULT_DIR_NAME).apply { if (!exists()) mkdirs() }
    }


    @get:JvmName("getStagingDirInternal")
    private val stagingDir: File by lazy {
        val externalDir = context.getExternalFilesDir(null)
        val base = externalDir ?: context.filesDir
        File(base, STAGING_DIR_NAME).apply { if (!exists()) mkdirs() }
    }

    override fun getStagingDir(): File = stagingDir


    private val stagedHides = ConcurrentHashMap<Long, StagedHide>()
    private val stagedIdGenerator = AtomicLong(-1L)

    private data class StagedHide(
        val vaultFile: File,
        val entity: HiddenVideoEntity,
    )

    override fun observeHiddenVideos(): Flow<List<Video>> {
        return hiddenVideoDao.getAll().map { entities -> entities.map { it.toVideo(context) } }
    }

    override suspend fun hasHiddenVideos(): Boolean = withContext(Dispatchers.IO) {
        hiddenVideoDao.count() > 0
    }

    override suspend fun hideVideo(originalUri: String, movedFile: File?, originalPath: String?): HideResult? = withContext(Dispatchers.IO) {
        val sourceUri = originalUri.toUri()

        // Use path from caller if available (most reliable — avoids URI resolution entirely).
        // Fall back to DocumentsProvider resolution, then MediaStore DATA column query.
        val resolvedFile: File? = when {
            originalPath != null -> File(originalPath)
            else -> resolveFile(originalUri) ?: resolveMediaInfo(sourceUri).second?.let { File(it) }
        }
        val originalPathKey = resolvedFile?.absolutePath

        // Deduplicate: if already hidden, return the existing entry.
        if (originalPathKey != null) {
            hiddenVideoDao.getByOriginalPath(originalPathKey)?.let { existing ->
                return@withContext HideResult.Hidden(existing.toVideo(context))
            }
        }

        runCatching {
            // Priority: real filename > MediaStore DISPLAY_NAME > URI fallback > timestamp
            val displayName = resolvedFile?.name?.takeIf { it.isNotBlank() }
                ?: resolveMediaInfo(sourceUri).first?.takeIf { it.isNotBlank() }
                ?: context.getFilenameFromUri(sourceUri).takeIf { it.isNotBlank() }
                ?: "video_${System.currentTimeMillis()}"

            val safeExtension = (resolvedFile?.extension ?: displayName.substringAfterLast('.', ""))
                .takeIf { it.isNotBlank() } ?: "mp4"
            val vaultFile = File(vaultDir, "${UUID.randomUUID()}.$safeExtension")

   
            if (movedFile != null) {
                val committed = movedFile.renameTo(vaultFile) || copyUriToFile(Uri.fromFile(movedFile), vaultFile)
                runCatching { if (movedFile.exists()) movedFile.delete() }
                if (!committed || vaultFile.length() == 0L) {
                    runCatching { vaultFile.delete() }
                    return@runCatching null
                }

                val (width, height, durationMs) = readVideoMetadata(vaultFile)
                val entity = HiddenVideoEntity(
                    vaultPath = vaultFile.absolutePath,
                    originalPath = originalPathKey ?: vaultFile.name,
                    displayName = displayName,
                    duration = durationMs,
                    size = vaultFile.length(),
                    width = width,
                    height = height,
                    hiddenAt = System.currentTimeMillis(),
                )
                val id = hiddenVideoDao.insert(entity)
                return@runCatching HideResult.Hidden(entity.copy(id = id).toVideo(context))
            }

            val movedDirectly = resolvedFile != null && tryMoveFile(resolvedFile, vaultFile)

            if (!movedDirectly) {
           
                if (!copyUriToFile(sourceUri, vaultFile)) return@runCatching null
            }
            if (vaultFile.length() == 0L) {
                vaultFile.delete()
                return@runCatching null
            }

            val (width, height, durationMs) = readVideoMetadata(vaultFile)
            val entity = HiddenVideoEntity(
                vaultPath = vaultFile.absolutePath,
                originalPath = originalPathKey ?: vaultFile.name,
                displayName = displayName,
                duration = durationMs,
                size = vaultFile.length(),
                width = width,
                height = height,
                hiddenAt = System.currentTimeMillis(),
            )

            if (movedDirectly) {
                // The original is already gone - nothing left to clean up. Safe to commit
                // the vault record right away.
                val id = hiddenVideoDao.insert(entity)
                HideResult.Hidden(entity.copy(id = id).toVideo(context))
            } else {

                val stagedId = stagedIdGenerator.getAndDecrement()
                val stagedEntity = entity.copy(id = stagedId)
                stagedHides[stagedId] = StagedHide(vaultFile, stagedEntity)
                HideResult.Staged(stagedEntity.toVideo(context), sourceUri)
            }
        }.getOrNull()
    }

    override suspend fun confirmStagedHides(confirmedIds: List<Long>, rolledBackIds: List<Long>) =
        withContext(Dispatchers.IO) {
            for (id in confirmedIds) {
                val staged = stagedHides.remove(id) ?: continue
                // The original was successfully deleted by the caller: commit the vault
                // record now, with a real, persisted id.
                hiddenVideoDao.insert(staged.entity.copy(id = 0))
            }
            for (id in rolledBackIds) {
                val staged = stagedHides.remove(id) ?: continue
                // The original could not be deleted (dialog cancelled, or deletion failed for
                // another reason): drop the vault copy so the video isn't duplicated in both
                // the vault and its original folder.
                runCatching { staged.vaultFile.delete() }
            }
        }


    private fun tryMoveFile(source: File, destination: File): Boolean {
        return runCatching {
            source.exists() && source.renameTo(destination) && destination.exists() && destination.length() > 0
        }.getOrDefault(false)
    }

    override suspend fun unhideVideos(ids: List<Long>): List<Long> = withContext(Dispatchers.IO) {
        val failed = mutableListOf<Long>()
        for (id in ids) {
            val success = runCatching {
                val entity = hiddenVideoDao.getById(id) ?: return@runCatching false
                val vaultFile = File(entity.vaultPath)
                if (!vaultFile.exists()) {
                    // Nothing to restore, just drop the orphaned record.
                    hiddenVideoDao.delete(entity)
                    return@runCatching true
                }

                val restoredUri = restoreToMediaStore(vaultFile, entity.displayName, entity.originalPath)
                    ?: return@runCatching false

                // Only delete the vault copy once the restored copy is confirmed written, so a
                // failed restore never leaves the video missing from both places.
                vaultFile.delete()
                hiddenVideoDao.delete(entity)
                context.scanPaths(listOf(restoredUri.toString()))
                true
            }.getOrDefault(false)

            if (!success) failed.add(id)
        }
        failed
    }

    private fun restoreToMediaStore(vaultFile: File, displayName: String, originalPath: String): Uri? {
        val resolver = context.contentResolver
        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(vaultFile.extension.lowercase())
            ?: "video/mp4"

        val target = resolveRestoreTarget(originalPath)

        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, target.relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            // insert() itself can throw IllegalArgumentException if RELATIVE_PATH's top-level
            // folder isn't one this collection accepts - keeping this inside the try means
            // that's handled the same way as any other restore failure instead of crashing
            // the unhide call.
            val itemUri = resolver.insert(target.collection, values) ?: return null

            resolver.openOutputStream(itemUri)?.use { output ->
                FileInputStream(vaultFile).use { input ->
                    input.copyTo(output)
                }
            } ?: run {
                resolver.delete(itemUri, null, null)
                return null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val pendingValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(itemUri, pendingValues, null, null)
            }
            itemUri
        } catch (e: Exception) {
            null
        }
    }

    private data class RestoreTarget(val collection: Uri, val relativePath: String)
    
    private fun resolveRestoreTarget(originalPath: String): RestoreTarget {
        val storageRoot = Environment.getExternalStorageDirectory()?.path
        val parent = File(originalPath).parent
        val videoCollection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        if (storageRoot == null || parent == null || !parent.startsWith(storageRoot)) {
            return RestoreTarget(videoCollection, "Movies")
        }

        val relative = parent.removePrefix(storageRoot).trim('/')
        if (relative.isBlank()) return RestoreTarget(videoCollection, "Movies")

        val topLevelDir = relative.substringBefore('/')
        return when (topLevelDir) {
            in ALLOWED_VIDEO_TOP_LEVEL_DIRS -> RestoreTarget(videoCollection, relative)
            Environment.DIRECTORY_DOWNLOADS -> {
                val downloadsCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                } else {
                    // MediaStore.Downloads doesn't exist before API 29; nothing under Download
                    // is reliably restorable as a video pre-Q, so fall back to Movies.
                    return RestoreTarget(videoCollection, "Movies")
                }
                RestoreTarget(downloadsCollection, relative)
            }
            else -> RestoreTarget(videoCollection, "Movies")
        }
    }

    override suspend fun deleteHiddenVideos(ids: List<Long>) = withContext(Dispatchers.IO) {
        for (id in ids) {
            val entity = hiddenVideoDao.getById(id) ?: continue
            runCatching { File(entity.vaultPath).delete() }
        }
        hiddenVideoDao.deleteByIds(ids)
    }

    override suspend fun getHiddenVideoInfo(id: Long): MediaInfo? = withContext(Dispatchers.IO) {
        val entity = hiddenVideoDao.getById(id) ?: return@withContext null
        val video = entity.toVideo(context)
        val mediaInfo = runCatching {
            MediaInfoBuilder().from(context = context, uri = video.uriString.toUri()).build()
        }.getOrNull()
        val result = MediaInfo(
            video = video.copy(format = mediaInfo?.format),
            videoStream = mediaInfo?.videoStream?.toVideoStreamInfo(),
            audioStreams = mediaInfo?.audioStreams?.map { it.toAudioStreamInfo() } ?: emptyList(),
            subtitleStreams = mediaInfo?.subtitleStreams?.map { it.toSubtitleStreamInfo() } ?: emptyList(),
        )
        mediaInfo?.release()
        result
    }

    /**
     * Resolves a `content://` (MediaStore/document) or `file://` uri string to the underlying
     * [File], reusing the same path-resolution logic the rest of the app relies on.
     */
    private fun resolveFile(uriString: String): File? {
        val path = context.getPath(uriString.toUri()) ?: return null
        return File(path)
    }


    private fun resolveMediaInfo(uri: Uri): Pair<String?, String?> {
        val projection = arrayOf(
            OpenableColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
        )
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use Pair(null, null)
                val name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    .takeIf { it >= 0 }?.let { cursor.getString(it) }
                val data = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    .takeIf { it >= 0 }?.let { cursor.getString(it) }
                Pair(name, data)
            } ?: Pair(null, null)
        }.getOrDefault(Pair(null, null))
    }

    private fun copyUriToFile(sourceUri: Uri, destination: File): Boolean {
        return runCatching {
            val input = context.contentResolver.openInputStream(sourceUri) ?: return@runCatching false
            input.use { stream ->
                FileOutputStream(destination).use { output ->
                    stream.copyTo(output)
                }
            }
            true
        }.getOrDefault(false)
    }

    private fun readVideoMetadata(file: File): Triple<Int, Int, Long> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            Triple(width, height, duration)
        } catch (e: Exception) {
            Triple(0, 0, 0)
        } finally {
            runCatching { retriever.release() }
        }
    }

    companion object {
        private const val VAULT_DIR_NAME = "vault"
        private const val STAGING_DIR_NAME = "vault_staging"

        private val ALLOWED_VIDEO_TOP_LEVEL_DIRS = setOf(
            Environment.DIRECTORY_DCIM,
            Environment.DIRECTORY_MOVIES,
            Environment.DIRECTORY_PICTURES,
        )
    }
}

private fun HiddenVideoEntity.toVideo(context: Context): Video {
    val fileUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        File(vaultPath),
    )
    val uriString = fileUri.toString()
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
