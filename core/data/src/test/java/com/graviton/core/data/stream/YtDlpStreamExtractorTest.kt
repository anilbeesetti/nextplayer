package com.graviton.core.data.stream

import com.graviton.core.model.StreamUrls
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpStreamExtractorTest {

    @Test
    fun resolve_returnsDirectMediaWithoutProcess() = runTest {
        val runner = RecordingProcessRunner()
        val extractor = YtDlpStreamExtractor(runner) { listOf("yt-dlp") }
        val result = extractor.resolve("https://cdn.example.com/movie.mp4")
        assertEquals("https://cdn.example.com/movie.mp4", result.playableUrl)
        assertFalse(result.extracted)
        assertTrue(runner.commands.isEmpty())
    }

    @Test
    fun resolve_parsesYtDlpJsonAndRetries() = runTest {
        val json = """
            {
              "title": "Demo",
              "uploader": "Channel",
              "thumbnail": "https://img.example.com/a.jpg",
              "duration": 10,
              "formats": [
                {
                  "format_id": "18",
                  "url": "https://cdn.example.com/progressive.mp4",
                  "ext": "mp4",
                  "protocol": "https",
                  "vcodec": "avc1",
                  "acodec": "mp4a",
                  "height": 360,
                  "tbr": 700
                }
              ]
            }
        """.trimIndent()
        val runner = RecordingProcessRunner(json)
        val extractor = YtDlpStreamExtractor(runner) { listOf("yt-dlp") }
        val result = extractor.resolve("https://www.youtube.com/watch?v=abc")
        assertEquals("https://cdn.example.com/progressive.mp4", result.playableUrl)
        assertEquals("Demo", result.title)
        assertEquals("Channel", result.uploader)
        assertEquals("https://img.example.com/a.jpg", result.thumbnailUrl)
        assertEquals(10_000L, result.durationMs)
        assertTrue(result.extracted)
        assertTrue(runner.commands.size >= 2)
    }

    @Test
    fun parser_prefersLargestThumbnail() {
        val json = """
            {
              "title": "Clip",
              "url": "https://cdn.example.com/fallback.mp4",
              "thumbnails": [
                {"url": "https://img.example.com/small.jpg", "width": 120},
                {"url": "https://img.example.com/large.jpg", "width": 1280}
              ]
            }
        """.trimIndent()
        val extracted = YtDlpJsonParser.parse("https://youtu.be/x", json)
        assertEquals("https://img.example.com/large.jpg", extracted.thumbnailUrl)
        assertEquals("https://cdn.example.com/fallback.mp4", extracted.playableUrl)
    }

    @Test
    fun websiteUrlsNeedExtraction() {
        assertTrue(StreamUrls.needsExtraction("https://soundcloud.com/artist/track"))
        assertFalse(StreamUrls.needsExtraction("https://files.example.com/a.webm"))
    }

private class RecordingProcessRunner(
        private val json: String = "",
    ) : ProcessRunner {
        val commands = mutableListOf<List<String>>()
        override fun run(command: List<String>, timeoutMs: Long): ProcessResult {
            commands += command
            if (command.lastOrNull() == "--version") {
                return ProcessResult(0, "2024.01.01", "")
            }
            if (command.contains("--dump-json") || command.contains("-J")) {
                return ProcessResult(0, json, "")
            }
            if (command.contains("-g")) {
                return ProcessResult(0, "https://cdn.example.com/progressive.mp4", "")
            }
            return ProcessResult(0, "", "")
        }
    }
}
