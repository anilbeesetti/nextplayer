package com.graviton.feature.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsParserTest {

    @Test
    fun parse_readsSyncedTimestamps() {
        val doc = LyricsParser.parse(
            """
            [00:12.00]First line
            [00:15.50]Second line
            [01:01.005]Third line
            """.trimIndent(),
        )
        assertTrue(doc.isSynced)
        assertEquals(3, doc.lines.size)
        assertEquals(12_000L, doc.lines[0].timeMs)
        assertEquals(15_500L, doc.lines[1].timeMs)
        assertEquals(61_005L, doc.lines[2].timeMs)
        assertEquals(1, doc.lineAt(16_000))
    }

    @Test
    fun parse_keepsUnsyncedText() {
        val doc = LyricsParser.parse("Just some lyrics\nWithout times")
        assertFalse(doc.isSynced)
        assertEquals("Just some lyrics\nWithout times", doc.unsynced)
    }
}
