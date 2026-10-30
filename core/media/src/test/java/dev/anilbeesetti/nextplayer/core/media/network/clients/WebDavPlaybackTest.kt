package dev.anilbeesetti.nextplayer.core.media.network.clients

import android.net.Uri
import androidx.media3.datasource.DataSpec
import com.sun.net.httpserver.HttpServer
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClientFactory
import dev.anilbeesetti.nextplayer.core.media.network.NetworkConnectionResolver
import dev.anilbeesetti.nextplayer.core.media.network.datasource.NetworkDataSource
import dev.anilbeesetti.nextplayer.core.media.network.datasource.NetworkSessions
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class WebDavPlaybackTest {
    @Test
    fun directFilePlaybackDoesNotRequireRootListing() {
        val requests = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.add("${exchange.requestMethod} ${exchange.requestURI.path}")
            exchange.requestBody.use { it.readBytes() }
            when {
                exchange.requestMethod == "HEAD" -> exchange.sendResponseHeaders(200, -1)
                exchange.requestMethod == "GET" && exchange.requestURI.path == "/Media/clip.mp4" -> {
                    val bytes = "video payload".toByteArray()
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
                else -> exchange.sendResponseHeaders(403, -1)
            }
            exchange.close()
        }
        server.start()
        try {
            val sessions = NetworkSessions(
                NetworkConnectionResolver { null },
                NetworkClientFactory { WebDavClient(it) },
            )
            val source = NetworkDataSource(sessions)
            try {
                source.open(DataSpec(Uri.parse("webdav://127.0.0.1:${server.address.port}/Media/clip.mp4")))
                val bytes = ByteArray(100)
                val count = source.read(bytes, 0, bytes.size)
                assertEquals("video payload", String(bytes, 0, count))
            } finally {
                source.close()
            }
        } finally {
            println("WebDAV playback requests: $requests")
            server.stop(0)
        }
    }
}
