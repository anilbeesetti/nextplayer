package dev.anilbeesetti.nextplayer.core.data.playlist

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
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

class LocalPlaylistSourceReader private constructor(
    private val ioDispatcher: CoroutineDispatcher,
    private val entryResolver: PlaylistEntryResolver,
    private val documentSourceOpener: PlaylistDocumentSourceOpener,
) : PlaylistSourceReader {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @Dispatcher(NextDispatchers.IO) ioDispatcher: CoroutineDispatcher,
        entryResolver: PlaylistEntryResolver,
    ) : this(
        ioDispatcher = ioDispatcher,
        entryResolver = entryResolver,
        documentSourceOpener = PlaylistDocumentSourceOpener { source ->
            context.contentResolver.openInputStream(source.toUri())
        },
    )

    internal constructor(
        ioDispatcher: CoroutineDispatcher,
        entryResolver: PlaylistEntryResolver,
        openDocumentStream: (String) -> InputStream?,
    ) : this(
        ioDispatcher = ioDispatcher,
        entryResolver = entryResolver,
        documentSourceOpener = PlaylistDocumentSourceOpener(openDocumentStream),
    )

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
            val declaredContentLength = connection.getHeaderField("Content-Length")?.trim()?.toLongOrNull()
            if (declaredContentLength != null && declaredContentLength > PlaylistLimits.MAX_SOURCE_BYTES) {
                throwSourceLimitExceeded()
            }

            val text = connection.inputStream.readPlaylistText()
            PlaylistSourceContent(text = text) { raw -> entryResolver.resolveRemote(source, raw) }
        } finally {
            connection.disconnect()
        }
    }

    private fun readDocument(source: String): PlaylistSourceContent {
        val text = documentSourceOpener.open(source)?.readPlaylistText()
            ?: throw IOException("Unable to open playlist source: $source")
        return PlaylistSourceContent(text = text) { raw -> entryResolver.resolveDocument(source, raw) }
    }

    private companion object {
        const val HTTP_TIMEOUT_MILLIS = 10_000
    }
}

private fun interface PlaylistDocumentSourceOpener {
    fun open(source: String): InputStream?
}

private fun InputStream.readPlaylistText(): String =
    SourceByteLimitedInputStream(this).reader(Charsets.UTF_8).use { reader ->
        val text = StringBuilder()
        val buffer = CharArray(DEFAULT_BUFFER_SIZE)
        var charsRead = 0
        while (true) {
            val readLimit = minOf(
                buffer.size,
                PlaylistLimits.MAX_SOURCE_CHARS - charsRead + 1,
            )
            val count = reader.read(buffer, 0, readLimit)
            if (count == -1) break
            if (charsRead + count > PlaylistLimits.MAX_SOURCE_CHARS) {
                throwSourceLimitExceeded()
            }
            text.append(buffer, 0, count)
            charsRead += count
        }
        text.toString()
    }

private class SourceByteLimitedInputStream(
    input: InputStream,
) : FilterInputStream(input) {
    private var bytesRead = 0

    override fun read(): Int = super.read().also { value ->
        if (value != -1) recordBytesRead(1)
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        val remainingWithProbe = PlaylistLimits.MAX_SOURCE_BYTES - bytesRead + 1
        return super.read(buffer, offset, minOf(length, remainingWithProbe)).also { count ->
            if (count > 0) recordBytesRead(count)
        }
    }

    private fun recordBytesRead(count: Int) {
        bytesRead += count
        if (bytesRead > PlaylistLimits.MAX_SOURCE_BYTES) throwSourceLimitExceeded()
    }
}

private fun throwSourceLimitExceeded(): Nothing = throw PlaylistSourceLimitExceededException(
    maxBytes = PlaylistLimits.MAX_SOURCE_BYTES,
    maxChars = PlaylistLimits.MAX_SOURCE_CHARS,
)
