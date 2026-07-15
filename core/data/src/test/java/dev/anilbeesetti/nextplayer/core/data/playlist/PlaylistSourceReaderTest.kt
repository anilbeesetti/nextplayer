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
            content = LocalPlaylistSourceReader(ContextWrapper(null), StandardTestDispatcher(testScheduler))
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
    }

    @Test
    fun rejectsNonSuccessfulHttpStatus() = withServer(status = 404, response = "missing") { source ->
        var error: Throwable? = null
        runTest {
            error = runCatching {
                LocalPlaylistSourceReader(ContextWrapper(null), StandardTestDispatcher(testScheduler))
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
}
