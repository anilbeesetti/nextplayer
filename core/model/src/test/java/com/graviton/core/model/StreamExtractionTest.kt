package com.graviton.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamExtractionTest {

    @Test
    fun needsExtraction_skipsDirectFilesAndLocalProxy() {
        assertFalse(StreamUrls.needsExtraction("https://cdn.example.com/movie.mp4"))
        assertFalse(StreamUrls.needsExtraction("https://cdn.example.com/audio.m4a?token=1"))
        assertFalse(StreamUrls.needsExtraction("http://127.0.0.1:8080/1/file.mkv"))
        assertFalse(StreamUrls.needsExtraction("content://media/external/video/media/1"))
        assertTrue(StreamUrls.needsExtraction("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(StreamUrls.needsExtraction("https://youtu.be/dQw4w9WgXcQ"))
    }

    @Test
    fun pick_prefersProgressiveHttpOverHls() {
        val hls = StreamFormatCandidate(
            url = "https://cdn.example.com/master.m3u8",
            extension = "m3u8",
            protocol = "m3u8_native",
            vcodec = "avc1",
            acodec = "mp4a",
            height = 1080,
        )
        val progressive = StreamFormatCandidate(
            url = "https://cdn.example.com/video.mp4",
            extension = "mp4",
            protocol = "https",
            vcodec = "avc1",
            acodec = "mp4a",
            height = 720,
            tbr = 2500.0,
        )
        assertEquals(progressive.url, StreamFormatSelector.pick(listOf(hls, progressive))?.url)
    }

    @Test
    fun pick_fallsBackToHlsThenAudio() {
        val hls = StreamFormatCandidate(
            url = "https://cdn.example.com/master.m3u8",
            extension = "m3u8",
            protocol = "m3u8_native",
            height = 480,
        )
        val audio = StreamFormatCandidate(
            url = "https://cdn.example.com/audio.m4a",
            extension = "m4a",
            protocol = "https",
            vcodec = "none",
            acodec = "mp4a",
            tbr = 128.0,
        )
        assertEquals(hls.url, StreamFormatSelector.pick(listOf(audio, hls))?.url)
        assertEquals(audio.url, StreamFormatSelector.pick(listOf(audio))?.url)
    }

    @Test
    fun select_usesTopLevelUrlWhenFormatsMissing() {
        val extracted = StreamFormatSelector.select(
            sourceUrl = "https://youtu.be/abc",
            title = "Song",
            uploader = "Artist",
            thumbnailUrl = "https://img.example.com/art.jpg",
            durationSeconds = 12.5,
            topLevelUrl = "https://cdn.example.com/stream.mp4",
            topLevelExt = "mp4",
            topLevelProtocol = "https",
            formats = emptyList(),
        )
        assertEquals("https://cdn.example.com/stream.mp4", extracted.playableUrl)
        assertEquals("Song", extracted.title)
        assertEquals(12500L, extracted.durationMs)
        assertTrue(extracted.extracted)
    }

    @Test
    fun extractedStream_detectsHlsAndDash() {
        assertTrue(
            ExtractedStream(
                sourceUrl = "https://youtu.be/a",
                playableUrl = "https://cdn.example.com/a.m3u8",
                protocol = "m3u8_native",
            ).isHls,
        )
        assertTrue(
            ExtractedStream(
                sourceUrl = "https://youtu.be/a",
                playableUrl = "https://cdn.example.com/a.mpd",
                extension = "mpd",
            ).isDash,
        )
    }
}
