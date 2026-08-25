package dev.anilbeesetti.nextplayer.core.data.playlist

import android.content.Context
import android.net.Uri
import com.sun.net.httpserver.HttpServer
import dev.anilbeesetti.nextplayer.core.model.M3UPlaylistItem
import java.net.InetSocketAddress
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class M3UParserTest {

    private lateinit var context: Context
    private lateinit var parser: M3UParser

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        parser = M3UParser(context, Dispatchers.Unconfined)
    }

    @Test
    fun parsesBomCrLfPlaylistNameAndExtendedMetadata() {
        val result = parser.parseContent(
            content = "\uFEFF#EXTM3U\r\n" +
                "#PLAYLIST:World News\r\n" +
                "#EXTINF:+42 TVG-NAME='Fallback' tvg-logo=\"logos/news.png\" " +
                "GROUP-TITLE=\"News, World\",Visible title\r\n" +
                "streams/channel.ts\r\n",
            source = remoteSource(),
        ).getOrThrow()

        assertEquals("World News", result.playlistName)
        assertEquals(1, result.items.size)
        assertEquals("https://example.com/lists/streams/channel.ts", result.items.single().uri)
        assertEquals("Visible title", result.items.single().title)
        assertEquals("https://example.com/lists/logos/news.png", result.items.single().tvgLogo)
        assertEquals(42, result.items.single().duration)
        assertEquals("News, World", result.items.single().groupTitle)
    }

    @Test
    fun appliesTitleArtworkAndDurationFallbacks() {
        val result = parser.parseContent(
            content = """
                #EXTM3U
                #EXTINF:not-a-number tvg-name="Named" logo='https://img.example/one.png',
                https://media.example/first.ts
                #EXTINF:999999999999999999999,
                #EXTIMG:https://img.example/two.png
                https://media.example/my_second-video.mp4
            """.trimIndent(),
            source = remoteSource(),
        ).getOrThrow()

        assertEquals("Named", result.items[0].title)
        assertEquals("https://img.example/one.png", result.items[0].tvgLogo)
        assertEquals(M3UPlaylistItem.UNKNOWN_DURATION, result.items[0].duration)
        assertEquals("my second video", result.items[1].title)
        assertEquals("https://img.example/two.png", result.items[1].tvgLogo)
        assertEquals(M3UPlaylistItem.UNKNOWN_DURATION, result.items[1].duration)
    }

    @Test
    fun acceptsSupportedAbsoluteSchemesAndDropsInvalidArtwork() {
        val result = parser.parseContent(
            content = """
                #EXTM3U
                #EXTINF:-1 tvg-logo="rtsp://example.com/logo",Stream
                rtsp://example.com/live
                https://example.com/video
                content://provider/videos/1
                file:///storage/emulated/0/video.mp4
                ftp://example.com/not-supported
            """.trimIndent(),
            source = documentSource(),
        ).getOrThrow()

        assertEquals(
            listOf(
                "rtsp://example.com/live",
                "https://example.com/video",
                "content://provider/videos/1",
                "file:///storage/emulated/0/video.mp4",
            ),
            result.items.map { it.uri },
        )
        assertNull(result.items.first().tvgLogo)
    }

    @Test
    fun resolvesFileEntriesButRejectsTraversalAndContentRelativeEntries() {
        val fileResult = parser.parseContent(
            content = "video.mp4\n../outside.mp4",
            source = M3USource.Document(
                uri = Uri.parse("file:///tmp/playlists/list.m3u"),
                fallbackName = "list.m3u",
            ),
        ).getOrThrow()
        val contentResult = parser.parseContent(
            content = "relative.mp4\nhttps://example.com/absolute.mp4",
            source = documentSource(),
        ).getOrThrow()

        assertEquals(listOf("file:/tmp/playlists/video.mp4"), fileResult.items.map { it.uri })
        assertEquals(
            listOf("https://example.com/absolute.mp4"),
            contentResult.items.map { it.uri },
        )
    }

    @Test
    fun deduplicatesResolvedUrisAndKeepsFirstMetadata() {
        val result = parser.parseContent(
            content = """
                #EXTINF:1,First
                https://example.com/video.mp4
                #EXTINF:2,Second
                https://example.com/video.mp4
            """.trimIndent(),
            source = remoteSource(),
        ).getOrThrow()

        assertEquals(1, result.items.size)
        assertEquals("First", result.items.single().title)
        assertEquals(1, result.items.single().duration)
    }

    @Test
    fun derivesDecodedSourceName() {
        val result = parser.parseContent(
            content = "https://example.com/video.mp4",
            source = M3USource.Remote(
                uri = URI("https://example.com/My%20_Favorites-list.M3U8"),
                fallbackName = "My%20_Favorites-list.M3U8",
            ),
        ).getOrThrow()

        assertEquals("My  Favorites list", result.playlistName)
    }

    @Test
    fun rejectsHlsManifestAndAllInvalidSources() {
        val hls = parser.parseContent(
            content = "#EXTM3U\n#ext-x-targetduration:10\nsegment.ts",
            source = remoteSource(),
        )
        val invalid = parser.parseContent(
            content = "#EXTM3U\nftp://example.com/video\nrelative.ts",
            source = documentSource(),
        )

        assertTrue(hls.isFailure)
        assertTrue(invalid.isFailure)
    }

    @Test
    fun allowsHlsUrlAsCatalogItem() {
        val result = parser.parseContent(
            content = "#EXTM3U\nhttps://example.com/live/master.m3u8",
            source = remoteSource(),
        )

        assertTrue(result.isSuccess)
        assertEquals("https://example.com/live/master.m3u8", result.getOrThrow().items.single().uri)
    }

    @Test
    fun parsesMoreThanTwentyThousandEntriesWithoutLimit() {
        val content = buildString {
            appendLine("#EXTM3U")
            repeat(20_001) { index ->
                appendLine("https://example.com/video/$index")
            }
        }

        val result = parser.parseContent(content, remoteSource()).getOrThrow()

        assertEquals(20_001, result.items.size)
    }

    @Test
    fun readsHttpUrlAndUsesSourceNameFallback() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/My_List.m3u") { exchange ->
            val body = "https://media.example/live".encodeToByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val result = runBlocking {
                parser.parseUrl("http://127.0.0.1:${server.address.port}/My_List.m3u")
            }.getOrThrow()

            assertEquals("My List", result.playlistName)
            assertEquals("https://media.example/live", result.items.single().uri)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun readsAbsoluteFileUri() {
        val file = kotlin.io.path.createTempFile(suffix = ".m3u").toFile()
        try {
            file.writeText("#PLAYLIST:File channels\nhttps://media.example/live")

            val result = runBlocking {
                parser.parseUri(Uri.fromFile(file))
            }.getOrThrow()

            assertEquals("File channels", result.playlistName)
            assertEquals("https://media.example/live", result.items.single().uri)
        } finally {
            file.delete()
        }
    }

    @Test
    fun cancellationIsNotConvertedToResultFailure() {
        val cancelledJob = Job().apply { cancel() }

        assertThrows(CancellationException::class.java) {
            runBlocking {
                withContext(cancelledJob) {
                    parser.parseUri(Uri.parse("content://provider/list.m3u"))
                }
            }
        }
    }

    private fun remoteSource() = M3USource.Remote(
        uri = URI("https://example.com/lists/catalog.m3u"),
        fallbackName = "catalog.m3u",
    )

    private fun documentSource() = M3USource.Document(
        uri = Uri.parse("content://provider/documents/catalog.m3u"),
        fallbackName = "catalog.m3u",
    )
}
