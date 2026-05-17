package dev.anilbeesetti.nextplayer.core.media.sync

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.extensions.getPath
import dev.anilbeesetti.nextplayer.core.media.model.MediaVideo
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Walks user-picked SAF tree URIs (which, unlike MediaStore, expose hidden
 * "dot" folders) and returns the video files found inside them.
 */
class DocumentTreeScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun scan(treeUris: List<String>): List<MediaVideo> = withContext(Dispatchers.IO) {
        treeUris.flatMap { uriString ->
            runCatching { scanTree(Uri.parse(uriString)) }.getOrDefault(emptyList())
        }
    }

    private fun scanTree(treeUri: Uri): List<MediaVideo> {
        val result = mutableListOf<MediaVideo>()
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val pending = ArrayDeque<String>()
        pending.add(rootDocId)

        while (pending.isNotEmpty()) {
            val parentDocId = pending.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)

            val entries = mutableListOf<DocEntry>()
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1) ?: continue
                    entries.add(
                        DocEntry(
                            docId = cursor.getString(0),
                            name = name,
                            mime = cursor.getString(2),
                            size = cursor.getLong(3),
                            lastModified = cursor.getLong(4),
                        ),
                    )
                }
            }

            // Honor .nomedia for descendant folders, but always scan the
            // folder the user explicitly picked (the tree root).
            val isRoot = parentDocId == rootDocId
            if (!isRoot && entries.any { it.name == NOMEDIA }) continue

            for (entry in entries) {
                if (entry.mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    pending.add(entry.docId)
                } else if (isVideo(entry.mime, entry.name)) {
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, entry.docId)
                    result.add(
                        buildMediaVideo(docUri, entry.docId, entry.name, entry.size, entry.lastModified),
                    )
                }
            }
        }
        return result
    }

    private data class DocEntry(
        val docId: String,
        val name: String,
        val mime: String?,
        val size: Long,
        val lastModified: Long,
    )

    private fun buildMediaVideo(
        docUri: Uri,
        docId: String,
        name: String,
        size: Long,
        lastModified: Long,
    ): MediaVideo {
        // A stable, path-shaped string so the existing path-based DB grouping works.
        val path = context.getPath(docUri)
            ?: "/" + docId.substringAfter(':', name).trimStart('/')

        var duration = 0L
        var width = 0
        var height = 0
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, docUri)
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            runCatching { retriever.release() }
        }

        return MediaVideo(
            id = 0L,
            uri = docUri,
            size = size,
            width = width,
            height = height,
            data = path,
            duration = duration,
            dateModified = lastModified,
        )
    }

    private fun isVideo(mime: String?, name: String): Boolean {
        if (mime != null && mime.startsWith("video/")) return true
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTENSIONS
    }

    companion object {
        private const val NOMEDIA = ".nomedia"

        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "webm", "avi", "mov", "flv", "wmv", "m4v",
            "3gp", "ts", "m2ts", "mts", "mpg", "mpeg", "ogv", "vob",
        )
    }
}
