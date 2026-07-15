package dev.anilbeesetti.nextplayer.core.data.playlist

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

data class PlaylistSourceContent(
    val text: String,
    val resolveEntry: (String) -> String?,
)

interface PlaylistSourceReader {
    suspend fun read(type: PlaylistType, source: String): PlaylistSourceContent
}

class LocalPlaylistSourceReader @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(NextDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : PlaylistSourceReader {
    override suspend fun read(
        type: PlaylistType,
        source: String,
    ): PlaylistSourceContent = withContext(ioDispatcher) {
        when (type) {
            PlaylistType.M3U_URL -> readRemote(source)
            PlaylistType.M3U_FILE -> readDocument(source)
            PlaylistType.EDITABLE -> error("Editable playlists do not have a linked source")
        }
    }

    private fun readRemote(source: String): PlaylistSourceContent {
        val sourceUri = URI(source)
        require(sourceUri.scheme?.lowercase() in HTTP_SCHEMES) {
            "Remote playlist source must use HTTP or HTTPS"
        }
        val connection = URL(source).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = HTTP_TIMEOUT_MILLIS
            connection.readTimeout = HTTP_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = true

            val status = connection.responseCode
            if (status !in 200..299) {
                throw IOException("HTTP $status while reading playlist source")
            }

            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText()
            }
            PlaylistSourceContent(text = text) { raw -> resolveRemoteEntry(sourceUri, raw) }
        } finally {
            connection.disconnect()
        }
    }

    private fun readDocument(source: String): PlaylistSourceContent {
        val sourceUri = Uri.parse(source)
        val text = context.contentResolver.openInputStream(sourceUri)?.bufferedReader(Charsets.UTF_8)
            ?.use { reader -> reader.readText() }
            ?: throw IOException("Unable to open playlist source: $source")
        return PlaylistSourceContent(text = text) { raw -> resolveDocumentEntry(sourceUri, raw) }
    }

    private fun resolveRemoteEntry(source: URI, raw: String): String? = runCatching {
        val entry = URI(raw)
        val resolved = if (entry.isAbsolute) entry else source.resolve(entry)
        resolved.toString().takeIf { it.hasSupportedScheme() }
    }.getOrNull()

    private fun resolveDocumentEntry(source: Uri, raw: String): String? {
        val absolute = runCatching { URI(raw) }.getOrNull()
            ?.takeIf { it.isAbsolute }
            ?.toString()
        if (absolute != null) return absolute.takeIf { it.hasSupportedScheme() }

        val documentId = runCatching { DocumentsContract.getDocumentId(source) }.getOrNull()
            ?: return null
        val parentDocumentId = documentId.substringBeforeLast('/', missingDelimiterValue = "")
            .takeIf { it.isNotEmpty() }
            ?: return null
        val authority = source.authority ?: return null
        val siblingDocumentId = "$parentDocumentId/$raw"
        val siblingUri = if (source.pathSegments.firstOrNull() == "tree") {
            DocumentsContract.buildDocumentUriUsingTree(source, siblingDocumentId)
        } else {
            DocumentsContract.buildDocumentUri(authority, siblingDocumentId)
        }
        return siblingUri.toString()
    }

    private fun String.hasSupportedScheme(): Boolean = runCatching {
        URI(this).scheme?.lowercase() in SUPPORTED_ENTRY_SCHEMES
    }.getOrDefault(false)

    private companion object {
        const val HTTP_TIMEOUT_MILLIS = 10_000
        val HTTP_SCHEMES = setOf("http", "https")
        val SUPPORTED_ENTRY_SCHEMES = HTTP_SCHEMES + setOf("content", "file")
    }
}
