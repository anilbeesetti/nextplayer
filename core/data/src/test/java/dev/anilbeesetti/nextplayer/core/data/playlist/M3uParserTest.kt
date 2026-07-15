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
    fun rejectsMoreThanMaximumParsedEntries() {
        val text = buildString {
            repeat(ENTRY_LIMIT + 1) { index ->
                append("https://example.test/video/")
                append(index)
                append('\n')
            }
        }

        val error = assertFailsWith<PlaylistEntryLimitExceededException> {
            parser.parse(text) { it }
        }

        assertEquals("Playlist contains more than $ENTRY_LIMIT entries", error.message)
    }

    private companion object {
        const val ENTRY_LIMIT = 10_000
    }
}
