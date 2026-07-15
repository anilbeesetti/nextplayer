package dev.anilbeesetti.nextplayer.core.data.playlist

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import java.io.IOException
import java.net.HttpURLConnection
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
    private val entryResolver: PlaylistEntryResolver,
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
        require(entryResolver.isValidRemoteSource(source)) {
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
            PlaylistSourceContent(text = text) { raw -> entryResolver.resolveRemote(source, raw) }
        } finally {
            connection.disconnect()
        }
    }

    private fun readDocument(source: String): PlaylistSourceContent {
        val sourceUri = source.toUri()
        val text = context.contentResolver.openInputStream(sourceUri)?.bufferedReader(Charsets.UTF_8)
            ?.use { reader -> reader.readText() }
            ?: throw IOException("Unable to open playlist source: $source")
        return PlaylistSourceContent(text = text) { raw -> entryResolver.resolveDocument(source, raw) }
    }

    private companion object {
        const val HTTP_TIMEOUT_MILLIS = 10_000
    }
}
