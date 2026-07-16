package dev.anilbeesetti.nextplayer.core.data.playlist

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertFailsWith

class M3uParserTest {
    private val parser = M3uParser()

    @Test
    fun parsesExtendedM3uAndDeduplicates() {
        val text = """#EXTM3U
            #EXTINF:12,First title
            videos/one.mp4
            #EXTINF:-1,Duplicate title
            videos/one.mp4
            https://cdn.example/two.mp4
        """.trimIndent()

        val result = parser.parse(text) { raw ->
            URI("https://example.test/list.m3u").resolve(raw).toString()
        }

        assertEquals(
            listOf("https://example.test/videos/one.mp4", "https://cdn.example/two.mp4"),
            result.entries.map { it.uriString },
        )
        assertEquals("First title", result.entries.first().title)
        assertEquals(0, result.skippedEntries)
    }

    @Test
    fun stripsBomAndCrLfWhileIgnoringCommentsAndBlankLines() {
        val text = "\uFEFF#EXTM3U\r\n" +
            "#EXTINF:-1,Episode title\r\n" +
            "# an unrelated comment\r\n" +
            "\r\n" +
            "https://example.test/episode.mp4\r\n"

        val result = parser.parse(text) { it }

        assertEquals(
            listOf("https://example.test/episode.mp4"),
            result.entries.map { it.uriString },
        )
        assertEquals("Episode title", result.entries.single().title)
        assertEquals(0, result.skippedEntries)
    }

    @Test
    fun preservesAbsoluteSupportedUris() {
        val text = """https://example.test/video.mp4
            http://example.test/video.mp4
            content://media/external/video/1
            file:///storage/emulated/0/Movies/video.mp4
        """.trimIndent()

        val result = parser.parse(text) { raw ->
            raw.takeIf { URI(it).isAbsolute }
        }

        assertEquals(
            listOf(
                "https://example.test/video.mp4",
                "http://example.test/video.mp4",
                "content://media/external/video/1",
                "file:///storage/emulated/0/Movies/video.mp4",
            ),
            result.entries.map { it.uriString },
        )
        assertEquals(0, result.skippedEntries)
    }

    @Test
    fun countsDocumentRelativeEntriesThatCannotResolve() {
        val text = """#EXTM3U
            videos/one.mp4
            # ignored
            ../two.mp4
            content://media/external/video/3
        """.trimIndent()

        val result = parser.parse(text) { raw ->
            raw.takeIf { URI(it).isAbsolute }
        }

        assertEquals(
            listOf("content://media/external/video/3"),
            result.entries.map { it.uriString },
        )
        assertEquals(2, result.skippedEntries)
    }

    @Test
    fun countsMalformedAndUnsupportedEntriesRejectedByResolver() {
        val text = """video.mp4
            http:video.mp4
            https:/video.mp4
            content:media/external/1
            file:relative.mp4
            ftp://example.test/video.mp4
        """.trimIndent()
        val resolver = PlaylistEntryResolver()

        val result = parser.parse(text) { entry ->
            resolver.resolveRemote("https://example.test/playlists/list.m3u", entry)
        }

        assertEquals(
            listOf("https://example.test/playlists/video.mp4"),
            result.entries.map { it.uriString },
        )
        assertEquals(5, result.skippedEntries)
    }

    @Test
    fun parsesTitleAndArtworkWithQuoteAwareAttributes() {
        val result = parser.parse(
            """#EXTM3U
                #EXTINF:-1 TVG-NAME="Fallback" tvg-logo="images/logo,one.png" logo="legacy.png",Channel One
                streams/one.m3u8
            """.trimIndent(),
        ) { raw -> URI("https://example.test/lists/index.m3u").resolve(raw).toString() }

        assertEquals("Channel One", result.entries.single().title)
        assertEquals(
            "https://example.test/lists/images/logo,one.png",
            result.entries.single().imageUrl,
        )
    }

    @Test
    fun fallsBackToCaseInsensitiveTvgNameAndLogoAlias() {
        val result = parser.parse(
            """#EXTM3U
                #EXTINF:-1 TvG-NaMe='Fallback title' LoGo='images/fallback.png',
                video.mp4
            """.trimIndent(),
        ) { raw -> URI("https://example.test/list.m3u").resolve(raw).toString() }

        assertEquals("Fallback title", result.entries.single().title)
        assertEquals("https://example.test/images/fallback.png", result.entries.single().imageUrl)
    }

    @Test
    fun usesTvgLogoThenLogoThenExtImgPrecedence() {
        val result = parser.parse(
            """#EXTM3U
                #EXTINF:-1 tvg-logo="images/tvg.png" logo="images/legacy.png",Channel
                #EXTIMG:images/extimg.png
                one.mp4
                #EXTINF:-1 logo="images/legacy.png",Channel Two
                #EXTIMG:images/extimg.png
                two.mp4
            """.trimIndent(),
        ) { raw -> URI("https://example.test/list.m3u").resolve(raw).toString() }

        assertEquals(
            listOf(
                "https://example.test/images/tvg.png",
                "https://example.test/images/legacy.png",
            ),
            result.entries.map { it.imageUrl },
        )
    }

    @Test
    fun usesExtImgAsArtworkFallback() {
        val result = parser.parse(
            """#EXTM3U
                #EXTINF:-1,Channel
                #EXTIMG:images/extimg.png
                video.mp4
            """.trimIndent(),
        ) { raw -> URI("https://example.test/list.m3u").resolve(raw).toString() }

        assertEquals("https://example.test/images/extimg.png", result.entries.single().imageUrl)
    }

    @Test
    fun invalidArtworkDoesNotCountAsSkippedEntry() {
        val resolvedInputs = mutableListOf<String>()
        val result = parser.parse(
            """#EXTM3U
                #EXTINF:-1 tvg-logo="invalid-artwork",Channel
                https://example.test/video.mp4
            """.trimIndent(),
        ) { raw ->
            resolvedInputs += raw
            raw.takeIf { it.startsWith("https://") }
        }

        assertEquals(1, result.entries.size)
        assertEquals(null, result.entries.single().imageUrl)
        assertEquals(0, result.skippedEntries)
        assertEquals(
            listOf("https://example.test/video.mp4", "invalid-artwork"),
            resolvedInputs,
        )
    }

    @Test
    fun duplicateUriRetainsFirstTitleAndArtwork() {
        val result = parser.parse(
            """#EXTM3U
                #EXTINF:-1 tvg-logo="images/first.png",First title
                video.mp4
                #EXTINF:-1 tvg-logo="images/second.png",Second title
                video.mp4
            """.trimIndent(),
        ) { raw -> URI("https://example.test/list.m3u").resolve(raw).toString() }

        assertEquals(1, result.entries.size)
        assertEquals("First title", result.entries.single().title)
        assertEquals("https://example.test/images/first.png", result.entries.single().imageUrl)
    }

    @Test
    fun acceptsMaximumUniqueResolvedEntries() {
        val result = parser.parse(uniqueEntries(ENTRY_LIMIT)) { it }

        assertEquals(ENTRY_LIMIT, result.entries.size)
    }

    @Test
    fun acceptsObservedIptvEntryCount() {
        val result = parser.parse(uniqueEntries(IPTV_OBSERVED_ENTRIES)) { it }

        assertEquals(IPTV_OBSERVED_ENTRIES, result.entries.size)
    }

    @Test
    fun rejectsMoreThanMaximumUniqueResolvedEntries() {
        val error = assertFailsWith<PlaylistEntryLimitExceededException> {
            parser.parse(uniqueEntries(ENTRY_LIMIT + 1)) { it }
        }

        assertEquals("Playlist contains more than $ENTRY_LIMIT entries", error.message)
    }

    @Test
    fun duplicatesAndInvalidLinesDoNotConsumeEntryLimit() {
        val text = buildString {
            repeat(ENTRY_LIMIT) { index ->
                append("https://example.test/video/")
                append(index)
                append('\n')
            }
            repeat(100) {
                append("https://example.test/video/0\n")
                append("invalid-entry\n")
            }
        }

        val result = parser.parse(text) { raw ->
            raw.takeIf { it.startsWith("https://") }
        }

        assertEquals(ENTRY_LIMIT, result.entries.size)
        assertEquals(100, result.skippedEntries)
    }

    private fun uniqueEntries(count: Int): String = buildString {
        repeat(count) { index -> append("https://example.test/video/$index\n") }
    }

    private companion object {
        const val ENTRY_LIMIT = 20_000
        const val IPTV_OBSERVED_ENTRIES = 13_276
    }
}
