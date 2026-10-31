package dev.anilbeesetti.nextplayer.core.media.network.clients

import com.sun.net.httpserver.HttpServer
import com.thegrizzlylabs.sardineandroid.impl.SardineException
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkProtocol
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [35],
    manifest = Config.NONE,
)
class WebDavClientTest {

    @Test
    fun `connect and browse a subdirectory that rejects HEAD`() = runBlocking {
        DavServer().use { server ->
            val client = server.client(path = "/Steam")

            client.connect().getOrThrow()
            assertTrue(client.isConnected())
            val file = client.listFiles(client.rootPath).getOrThrow().single()
            assertEquals(
                "clip.mp4",
                file.name,
            )
            assertEquals(
                "clip.mp4",
                file.path,
            )
            assertFalse(file.isDirectory)
            assertEquals(
                123L,
                file.size,
            )
            assertEquals(
                listOf(
                    "PROPFIND",
                ),
                server.methods,
            )
            assertEquals(
                listOf(
                    "/Steam/",
                ),
                server.paths,
            )
            assertEquals(
                listOf(
                    "1",
                ),
                server.depths,
            )
            client.disconnect()
            assertFalse(client.isConnected())
        }
    }

    @Test
    fun `empty directories and server root remain valid connections`() = runBlocking {
        for (path in listOf(
            "/",
            "/Steam/",
            "/Media/Steam",
        )) {
            DavServer(includeFile = false).use { server ->
                val client = server.client(path = path)

                client.connect().getOrThrow()
                assertTrue(client.listFiles(client.rootPath).getOrThrow().isEmpty())
                assertTrue(server.paths.all { it == "/${path.trim('/')}".trimEnd('/') + "/" })
                client.disconnect()
            }
        }
    }

    @Test
    fun `listing authenticates PROPFIND after a basic auth challenge`() = runBlocking {
        DavServer(requireAuthentication = true).use { server ->
            val client = server.client(
                path = "/Steam/",
                username = "viewer",
                password = "secret",
            )

            client.connect().getOrThrow()
            client.listFiles(client.rootPath).getOrThrow()
            assertTrue(client.isConnected())
            assertTrue(
                server.authorizations.contains(
                    Credentials.basic(
                        "viewer",
                        "secret",
                    ),
                ),
            )
            assertTrue(server.methods.all { it == "PROPFIND" })
            client.disconnect()
        }
    }

    @Test
    fun `authentication permission missing path and server errors fail listing`() = runBlocking {
        for (status in listOf(
            401,
            403,
            404,
            500,
        )) {
            DavServer(status = status).use { server ->
                val client = server.client(path = "/Steam/")

                client.connect().getOrThrow()
                val error = client.listFiles(client.rootPath).exceptionOrNull()

                assertTrue(
                    "Expected HTTP $status failure, got $error",
                    error is SardineException,
                )
                assertEquals(
                    status,
                    (error as SardineException).statusCode,
                )
                assertEquals(
                    listOf("PROPFIND"),
                    server.methods,
                )
                client.disconnect()
            }
        }
    }

    @Test
    fun `invalid multistatus XML fails listing`() = runBlocking {
        DavServer(bodyOverride = "not WebDAV XML").use { server ->
            val client = server.client(path = "/Steam/")

            client.connect().getOrThrow()
            assertTrue(client.listFiles(client.rootPath).isFailure)
            client.disconnect()
        }
    }

    private class DavServer(
        private val status: Int = 207,
        private val includeFile: Boolean = true,
        private val requireAuthentication: Boolean = false,
        private val bodyOverride: String? = null,
    ) : AutoCloseable {
        private val server = HttpServer.create(
            InetSocketAddress(
                "127.0.0.1",
                0,
            ),
            0,
        )
        val methods = CopyOnWriteArrayList<String>()
        val paths = CopyOnWriteArrayList<String>()
        val depths = CopyOnWriteArrayList<String>()
        val authorizations = CopyOnWriteArrayList<String>()

        init {
            server.createContext("/") { exchange ->
                val method = exchange.requestMethod
                val path = exchange.requestURI.rawPath
                val authorization = exchange.requestHeaders.getFirst("Authorization").orEmpty()
                methods.add(method)
                paths.add(path)
                depths.add(exchange.requestHeaders.getFirst("Depth").orEmpty())
                authorizations.add(authorization)
                exchange.requestBody.use { it.readBytes() }
                val responseStatus = when {
                    method == "HEAD" -> 403

                    requireAuthentication && authorization != Credentials.basic(
                        "viewer",
                        "secret",
                    ) -> 401

                    else -> status
                }
                if (responseStatus == 401) {
                    exchange.responseHeaders.add(
                        "WWW-Authenticate",
                        "Basic realm=\"Test WebDAV\"",
                    )
                }
                if (responseStatus == 207) {
                    val file = if (includeFile) {
                        """
                        <d:response>
                          <d:href>${path}clip.mp4</d:href>
                          <d:propstat><d:prop>
                            <d:resourcetype/><d:getcontentlength>123</d:getcontentlength>
                          </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
                        </d:response>
                        """.trimIndent()
                    } else {
                        ""
                    }
                    val xml = bodyOverride ?: """
                        <?xml version="1.0" encoding="utf-8"?>
                        <d:multistatus xmlns:d="DAV:">
                          <d:response>
                            <d:href>$path</d:href>
                            <d:propstat><d:prop>
                              <d:resourcetype><d:collection/></d:resourcetype>
                            </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
                          </d:response>
                          $file
                        </d:multistatus>
                    """.trimIndent().trim()
                    val body = xml.toByteArray()
                    exchange.responseHeaders.add(
                        "Content-Type",
                        "application/xml; charset=utf-8",
                    )
                    exchange.sendResponseHeaders(
                        responseStatus,
                        body.size.toLong(),
                    )
                    exchange.responseBody.use { it.write(body) }
                } else {
                    exchange.sendResponseHeaders(
                        responseStatus,
                        -1,
                    )
                }
                exchange.close()
            }
            server.start()
        }

        fun client(path: String, username: String = "", password: String = "") = WebDavClient(
            connection = NetworkConnection(
                name = "Test WebDAV",
                protocol = NetworkProtocol.WEBDAV,
                host = "127.0.0.1",
                port = server.address.port,
                path = path,
                username = username,
                password = password,
            ),
        )

        override fun close() = server.stop(0)
    }
}
