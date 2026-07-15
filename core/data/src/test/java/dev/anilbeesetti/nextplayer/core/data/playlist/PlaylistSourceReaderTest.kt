package dev.anilbeesetti.nextplayer.core.data.playlist

import android.content.ContextWrapper
import com.sun.net.httpserver.HttpServer
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

class PlaylistSourceReaderTest {
    @Test
    fun readsHttpSourceAndResolvesRemoteEntries() = withServer(
        status = 200,
        response = "#EXTM3U\n#EXTINF:-1,Café\nvidéo.mp4",
    ) { source ->
        lateinit var content: PlaylistSourceContent
        runTest {
            content = LocalPlaylistSourceReader(
                ContextWrapper(null),
                StandardTestDispatcher(testScheduler),
                PlaylistEntryResolver(),
            )
                .read(PlaylistType.M3U_URL, source)
        }

        assertEquals("#EXTM3U\n#EXTINF:-1,Café\nvidéo.mp4", content.text)
        assertEquals(
            source.substringBeforeLast('/') + "/vidéo.mp4",
            content.resolveEntry("vidéo.mp4"),
        )
        assertEquals(
            "content://media/external/video/7",
            content.resolveEntry("content://media/external/video/7"),
        )
        listOf(
            "http:video.mp4",
            "https:/video.mp4",
            "content:media/external/1",
            "file:relative.mp4",
            "ftp://example.test/video.mp4",
        ).forEach { entry ->
            assertNull(entry, content.resolveEntry(entry))
        }
    }

    @Test
    fun validatesAbsoluteEntriesByScheme() {
        val resolver = PlaylistEntryResolver()

        listOf(
            "https://example.test/video.mp4",
            "http://example.test/video.mp4",
            "content://media/external/video/1",
            "file:///storage/emulated/0/Movies/video.mp4",
        ).forEach { entry ->
            assertEquals(entry, resolver.resolveDocument(DOCUMENT_SOURCE, entry))
        }
        listOf(
            "http:video.mp4",
            "https:/video.mp4",
            "content:media/external/1",
            "content://media",
            "content://media///",
            "file:relative.mp4",
            "file://relative.mp4",
            "ftp://example.test/video.mp4",
        ).forEach { entry ->
            assertNull(entry, resolver.resolveDocument(DOCUMENT_SOURCE, entry))
        }
    }

    @Test
    fun singleDocumentSourceRejectsRelativeEntries() {
        val resolver = PlaylistEntryResolver()

        assertNull(resolver.resolveDocument(DOCUMENT_SOURCE, "video.mp4"))
        assertNull(resolver.resolveDocument(DOCUMENT_SOURCE, "../video.mp4"))
    }

    @Test
    fun treeDocumentSourceResolvesOnlyNormalizedDescendants() {
        val resolver = PlaylistEntryResolver()

        assertEquals(
            "content://com.example.documents/tree/primary%3AMovies/document/" +
                "primary%3AMovies%2Fplaylists%2Fmedia%2Fvideo%201.mp4",
            resolver.resolveDocument(TREE_DOCUMENT_SOURCE, "media/video 1.mp4"),
        )
        assertEquals(
            "content://com.example.documents/tree/primary%3AMovies/document/" +
                "primary%3AMovies%2Fvideo.mp4",
            resolver.resolveDocument(TREE_ROOT_DOCUMENT_SOURCE, "video.mp4"),
        )
        listOf(
            "",
            ".",
            "./video.mp4",
            "../video.mp4",
            "media/../video.mp4",
            "media//video.mp4",
            "/video.mp4",
            "%2e%2e/video.mp4",
        ).forEach { entry ->
            assertNull(entry, resolver.resolveDocument(TREE_DOCUMENT_SOURCE, entry))
        }
        assertNull(
            resolver.resolveDocument(
                "content://com.example.documents/tree/primary%3AMovies/document/primary%3ADownloads%2Flist.m3u",
                "video.mp4",
            ),
        )
    }

    @Test
    fun rejectsNonSuccessfulHttpStatus() = withServer(status = 404, response = "missing") { source ->
        var error: Throwable? = null
        runTest {
            error = runCatching {
                LocalPlaylistSourceReader(
                    ContextWrapper(null),
                    StandardTestDispatcher(testScheduler),
                    PlaylistEntryResolver(),
                )
                    .read(PlaylistType.M3U_URL, source)
            }.exceptionOrNull()
        }

        assertTrue(error is IOException)
        assertTrue(error?.message.orEmpty().contains("404"))
    }

    @Test
    fun rejectsHttpSourceWithOversizedDeclaredLength() = withServer(
        status = 200,
        response = "",
        declaredLength = (SOURCE_LIMIT_BYTES + 1).toLong(),
    ) { source ->
        runTest {
            val error = assertFailsWith<PlaylistSourceLimitExceededException> {
                remoteReader(StandardTestDispatcher(testScheduler)).read(PlaylistType.M3U_URL, source)
            }

            assertEquals(SOURCE_LIMIT_BYTES, error.maxBytes)
        }
    }

    @Test
    fun rejectsOversizedChunkedHttpSource() = withServer(
        status = 200,
        response = "a".repeat(SOURCE_LIMIT_BYTES + 1),
        chunked = true,
    ) { source ->
        runTest {
            val error = assertFailsWith<PlaylistSourceLimitExceededException> {
                remoteReader(StandardTestDispatcher(testScheduler)).read(PlaylistType.M3U_URL, source)
            }

            assertTrue(error.message.orEmpty().contains(SOURCE_LIMIT_BYTES.toString()))
        }
    }

    @Test
    fun acceptsHttpSourceAtExactSizeBoundary() = withServer(
        status = 200,
        response = "a".repeat(SOURCE_LIMIT_BYTES),
    ) { source ->
        runTest {
            val content = remoteReader(StandardTestDispatcher(testScheduler)).read(PlaylistType.M3U_URL, source)

            assertEquals(SOURCE_LIMIT_BYTES, content.text.length)
        }
    }

    @Test
    fun rejectsOversizedContentResolverStreamAndClosesIt() = runTest {
        val stream = CloseTrackingInputStream(
            ByteArrayInputStream(ByteArray(SOURCE_LIMIT_BYTES + 1) { 'a'.code.toByte() }),
        )
        val reader = documentReader(StandardTestDispatcher(testScheduler)) { stream }

        val error = assertFailsWith<PlaylistSourceLimitExceededException> {
            reader.read(PlaylistType.M3U_FILE, DOCUMENT_SOURCE)
        }

        assertTrue(error.message.orEmpty().contains(SOURCE_LIMIT_BYTES.toString()))
        assertTrue(stream.isClosed)
    }

    @Test
    fun readsUtf8DocumentStreamAndClosesIt() = runTest {
        val expected = "#EXTM3U\n#EXTINF:-1,Café\ncontent://media/external/video/7"
        val stream = CloseTrackingInputStream(
            ByteArrayInputStream(expected.toByteArray(StandardCharsets.UTF_8)),
        )
        val reader = documentReader(StandardTestDispatcher(testScheduler)) { stream }

        val content = reader.read(PlaylistType.M3U_FILE, DOCUMENT_SOURCE)

        assertEquals(expected, content.text)
        assertEquals(
            "content://media/external/video/7",
            content.resolveEntry("content://media/external/video/7"),
        )
        assertTrue(stream.isClosed)
    }

    @Test
    fun continuousDocumentStreamReadsOnlyOneBytePastLimitAndClosesIt() = runTest {
        val stream = ContinuousInputStream()
        val reader = documentReader(StandardTestDispatcher(testScheduler)) { stream }

        assertFailsWith<PlaylistSourceLimitExceededException> {
            reader.read(PlaylistType.M3U_FILE, DOCUMENT_SOURCE)
        }

        assertEquals(SOURCE_LIMIT_BYTES + 1, stream.bytesRead)
        assertTrue(stream.isClosed)
    }

    private fun remoteReader(dispatcher: CoroutineDispatcher) = LocalPlaylistSourceReader(
        ContextWrapper(null),
        dispatcher,
        PlaylistEntryResolver(),
    )

    private fun documentReader(
        dispatcher: CoroutineDispatcher,
        openDocumentStream: (String) -> InputStream?,
    ) = LocalPlaylistSourceReader(dispatcher, PlaylistEntryResolver(), openDocumentStream)

    private fun withServer(
        status: Int,
        response: String,
        chunked: Boolean = false,
        declaredLength: Long? = null,
        test: (String) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/playlist/list.m3u") { exchange ->
            val bytes = response.toByteArray(StandardCharsets.UTF_8)
            val responseLength = declaredLength ?: if (chunked) 0 else bytes.size.toLong()
            exchange.sendResponseHeaders(status, responseLength)
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            test("http://127.0.0.1:${server.address.port}/playlist/list.m3u")
        } finally {
            server.stop(0)
        }
    }

    private class CloseTrackingInputStream(
        delegate: InputStream,
    ) : InputStream() {
        var isClosed = false
            private set

        private val input = delegate

        override fun read(): Int = input.read()

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            input.read(buffer, offset, length)

        override fun close() {
            isClosed = true
            input.close()
        }
    }

    private class ContinuousInputStream : InputStream() {
        var bytesRead = 0
            private set
        var isClosed = false
            private set

        override fun read(): Int {
            bytesRead++
            return 'a'.code
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            buffer.fill('a'.code.toByte(), offset, offset + length)
            bytesRead += length
            return length
        }

        override fun close() {
            isClosed = true
        }
    }

    private companion object {
        const val SOURCE_LIMIT_BYTES = 1_048_576
        const val DOCUMENT_SOURCE =
            "content://com.example.documents/document/primary%3AMovies%2Fplaylists%2Flist.m3u"
        const val TREE_DOCUMENT_SOURCE =
            "content://com.example.documents/tree/primary%3AMovies/document/" +
                "primary%3AMovies%2Fplaylists%2Flist.m3u"
        const val TREE_ROOT_DOCUMENT_SOURCE =
            "content://com.example.documents/tree/primary%3AMovies/document/" +
                "primary%3AMovies%2Flist.m3u"
    }
}
