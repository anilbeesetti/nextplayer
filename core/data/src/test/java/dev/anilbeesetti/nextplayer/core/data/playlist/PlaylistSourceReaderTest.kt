package dev.anilbeesetti.nextplayer.core.data.playlist

import android.content.ContextWrapper
import com.sun.net.httpserver.HttpServer
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistSourceReaderTest {
    @Test
    fun readsHttpSourceAndResolvesRemoteEntries() = withServer(
        status = 200,
        response = "#EXTM3U\nvideo.mp4",
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

        assertEquals("#EXTM3U\nvideo.mp4", content.text)
        assertEquals(
            source.substringBeforeLast('/') + "/video.mp4",
            content.resolveEntry("video.mp4"),
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

    private fun withServer(
        status: Int,
        response: String,
        test: (String) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/playlist/list.m3u") { exchange ->
            val bytes = response.toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            test("http://127.0.0.1:${server.address.port}/playlist/list.m3u")
        } finally {
            server.stop(0)
        }
    }

    private companion object {
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
