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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class LocalVaultRepository @Inject constructor(
    private val hiddenVideoDao: HiddenVideoDao,
    private val mediaOperationsService: MediaOperationsService,
    @ApplicationContext private val context: Context,
) : VaultRepository {

    private val vaultDir: File by lazy {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        File(base, VAULT_DIR_NAME).apply { if (!exists()) mkdirs() }
    }

    override fun observeHiddenVideos(): Flow<List<Video>> {
        return hiddenVideoDao.getAll().map { entities -> entities.map { it.toVideo() } }
    }

    override suspend fun hideVideos(videos: List<Video>) {
        val movedFiles = mediaOperationsService.moveMedia(
            uris = videos.map { it.uriString.toUri() },
            targetDir = vaultDir,
        )

        videos.forEach { video ->
            val vaultFile = movedFiles[video.uriString.toUri()] ?: return@forEach
            hiddenVideoDao.insert(
                HiddenVideoEntity(
                    vaultPath = vaultFile.absolutePath,
                    originalPath = video.path,
                    displayName = video.nameWithExtension,
                    duration = video.duration,
                    size = video.size,
                    width = video.width,
                    height = video.height,
                    hiddenAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun unhideVideos(videos: List<Video>): UnhideResult {
        val restored = withContext(Dispatchers.IO) {
            entitiesFor(videos).mapNotNull { entity ->
                restoreToMediaStore(entity)?.let { relocated -> entity.id to relocated }
            }
        }
        if (restored.isNotEmpty()) hiddenVideoDao.deleteByIds(restored.map { it.first })
        return UnhideResult(relocatedCount = restored.count { (_, relocated) -> relocated })
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

    override suspend fun deleteHiddenVideos(videos: List<Video>) {
        val entities = entitiesFor(videos)
        if (entities.isEmpty()) return
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
        private const val VAULT_DIR_NAME = "vault"

        /** Fallback folder for videos whose original location MediaStore won't accept. */
        private val DEFAULT_RESTORE_DIR = Environment.DIRECTORY_MOVIES + "/"
    }
}
