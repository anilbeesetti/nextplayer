package dev.anilbeesetti.nextplayer.core.media.network.proxy

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClient
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClientFactory
import dev.anilbeesetti.nextplayer.core.media.network.networkVideoMimeType
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import fi.iki.elonen.NanoHTTPD
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * A local HTTP server on `127.0.0.1` that bridges network protocols (SMB/FTP/WebDAV) to plain HTTP
 * so the Media3 player can stream and seek remote files without a custom data source.
 *
 * A caller [registerStream]s a remote file and receives a `http://127.0.0.1:<port>/<id>/<name>` URL
 * to hand to the player. Incoming HTTP range requests are translated into offset reads on the
 * appropriate [NetworkClient].
 *
 * Only one network stream is played at a time, so [registerStream] releases any previously
 * registered stream (and its connection) instead of accumulating them for the app's lifetime.
 *
 * The NanoHTTPD server is composed (not inherited) so the dependency does not leak to callers.
 */
@Singleton
class NetworkStreamingProxy @Inject constructor() {

    private data class StreamInfo(
        val client: NetworkClient,
        val filePath: String,
        val mimeType: String,
        var fileSize: Long = -1L,
    )

    private val streams = ConcurrentHashMap<String, StreamInfo>()
    private val idCounter = AtomicLong(0)
    private var server: ProxyServer? = null

    @Synchronized
    private fun ensureStarted(): Int {
        val running = server ?: ProxyServer().also {
            it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            server = it
        }
        return running.listeningPort
    }

    /** Registers [filePath] on [connection] for streaming and returns the local playback URL. */
    @Synchronized
    fun registerStream(connection: NetworkConnection, filePath: String, fileName: String): String {
        val port = ensureStarted()
        releasePreviousStreams()
        val id = idCounter.incrementAndGet().toString()
        streams[id] = StreamInfo(
            client = NetworkClientFactory.create(connection),
            filePath = filePath,
            mimeType = networkVideoMimeType(fileName),
        )
        // The stream id is the first path segment; the (encoded) file name is appended only so the
        // player can derive a proper title from the URL's last segment instead of the id.
        return "http://127.0.0.1:$port/$id/${Uri.encode(fileName)}"
    }

    /**
     * Stops the local server and releases every stream and connection. Call when the app is being
     * destroyed. The proxy stays reusable — a later [registerStream] lazily starts a new server.
     */
    @Synchronized
    fun release() {
        releasePreviousStreams()
        server?.stop()
        server = null
    }

    /** Drops all registered streams and closes their connections off the caller's thread. */
    private fun releasePreviousStreams() {
        if (streams.isEmpty()) return
        val previous = streams.values.toList()
        streams.clear()
        CoroutineScope(Dispatchers.IO).launch {
            previous.forEach { runCatching { it.client.disconnect() } }
        }
    }

    private inner class ProxyServer : NanoHTTPD("127.0.0.1", 0) {

        override fun serve(session: IHTTPSession): Response {
            val id = session.uri.trim('/').substringBefore('/')
            val info = streams[id]
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Stream not found")

            return try {
                runBlocking {
                    if (!info.client.isConnected()) info.client.connect().getOrThrow()
                    if (info.fileSize < 0) info.fileSize = info.client.fileSize(info.filePath)

                    val range = session.headers["range"]
                    if (range != null && range.startsWith("bytes=") && info.fileSize > 0) {
                        partialResponse(info, range)
                    } else {
                        fullResponse(info)
                    }
                }
            } catch (e: Exception) {
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error: ${e.message}")
            }
        }

        private suspend fun partialResponse(info: StreamInfo, rangeHeader: String): Response {
            val parts = rangeHeader.removePrefix("bytes=").split("-")
            val start = parts.getOrNull(0)?.toLongOrNull() ?: 0L
            val end = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.toLongOrNull() ?: (info.fileSize - 1)
            val contentLength = (end - start + 1).coerceAtLeast(0)

            val stream = info.client.openStream(info.filePath, start)
            return newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, info.mimeType, stream, contentLength).apply {
                addHeader("Accept-Ranges", "bytes")
                addHeader("Content-Range", "bytes $start-$end/${info.fileSize}")
            }
        }

        private suspend fun fullResponse(info: StreamInfo): Response {
            val stream = info.client.openStream(info.filePath, 0L)
            return if (info.fileSize >= 0) {
                newFixedLengthResponse(Response.Status.OK, info.mimeType, stream, info.fileSize).apply {
                    addHeader("Accept-Ranges", "bytes")
                }
            } else {
                newChunkedResponse(Response.Status.OK, info.mimeType, stream)
            }
        }
    }
}
