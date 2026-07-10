package dev.anilbeesetti.nextplayer.core.data.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {

    @Test
    fun `parses extinf attributes name and url`() {
        val content = """
            #EXTM3U
            #EXTINF:-1 tvg-id="bbc1" tvg-logo="http://logo/bbc.png" group-title="News",BBC One HD
            http://host/live/bbc1.m3u8
        """.trimIndent()

        val channels = M3uParser.parse(content)

        assertEquals(1, channels.size)
        val channel = channels.first()
        assertEquals("BBC One HD", channel.name)
        assertEquals("http://host/live/bbc1.m3u8", channel.url)
        assertEquals("http://logo/bbc.png", channel.logoUrl)
        assertEquals("News", channel.groupTitle)
        assertEquals("bbc1", channel.tvgId)
        assertTrue(channel.isLive)
    }

    @Test
    fun `duration greater than zero is treated as on-demand`() {
        val content = """
            #EXTM3U
            #EXTINF:120 ,Some Movie
            http://host/vod/movie.mp4
        """.trimIndent()

        val channel = M3uParser.parse(content).single()
        assertFalse(channel.isLive)
        assertEquals("Some Movie", channel.name)
    }

    @Test
    fun `skips provider option lines between extinf and url`() {
        val content = """
            #EXTM3U
            #EXTINF:-1,Protected Channel
            #EXTVLCOPT:http-user-agent=SomeAgent
            #KODIPROP:inputstream.adaptive.license_type=clearkey
            http://host/live/protected.m3u8
        """.trimIndent()

        val channel = M3uParser.parse(content).single()
        assertEquals("Protected Channel", channel.name)
        assertEquals("http://host/live/protected.m3u8", channel.url)
    }

    @Test
    fun `bare url playlist without metadata still yields channels`() {
        val content = """
            http://host/a.m3u8
            http://host/b.m3u8
        """.trimIndent()

        val channels = M3uParser.parse(content)
        assertEquals(2, channels.size)
        assertEquals("a.m3u8", channels.first().name)
    }

    @Test
    fun `empty attributes become null`() {
        val content = """
            #EXTM3U
            #EXTINF:-1 tvg-logo="" group-title="",Plain
            http://host/plain.ts
        """.trimIndent()

        val channel = M3uParser.parse(content).single()
        assertNull(channel.logoUrl)
        assertNull(channel.groupTitle)
    }
}
