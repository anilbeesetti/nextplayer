package com.graviton.feature.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsParserTest {
    @Test fun parse_readsSyncedTimestamps() {
        val doc = LyricsParser.parse("[00:12.00]First line\n[00:15.50]Second line\n[01:01.005]Third line")
        assertTrue(doc.isSynced)
        assertEquals(listOf(12_000L, 15_500L, 61_005L), doc.lines.map { it.timeMs })
        assertEquals(1, doc.lineAt(16_000))
    }

    @Test fun parse_keepsUnsyncedText() {
        val doc = LyricsParser.parse("Just some lyrics\nWithout times")
        assertFalse(doc.isSynced)
        assertEquals("Just some lyrics\nWithout times", doc.unsynced)
    }

    @Test fun lrc_supportsMultipleTimestampsOffsetAndBlankLines() {
        val doc = LyricsParser.parse("[offset:-250]\n[00:01.00][00:02.5]Echo\n[00:03.00]")
        assertEquals(-250L, doc.offsetMs)
        assertEquals(listOf(750L, 2_250L, 2_750L), doc.lines.map { it.timeMs })
        assertEquals("", doc.lines.last().text)
    }

    @Test fun lrc_mergesTranslationAtSameTimestamp() {
        val doc = LyricsParser.parse("[00:01]Hello\n[00:01]Hola")
        assertEquals(1, doc.lines.size)
        assertEquals("Hola", doc.lines.single().translation)
    }

    @Test fun enhancedLrc_readsWordTiming() {
        val doc = LyricsParser.parse("[00:01.00]<00:01.00>Hello <00:01.50>world")
        assertTrue(doc.hasWordTiming)
        assertEquals(2, doc.lines.single().words.size)
        assertEquals(1_500L, doc.lines.single().words.last().startMs)
    }

    @Test fun ttml_readsLinesWordsTranslationAndVoice() {
        val raw = """<?xml version="1.0"?><tt xmlns="http://www.w3.org/ns/ttml"><body><div>
            <p begin="00:00:01.000" end="00:00:03.000" agent="v1"><span begin="00:00:01.000" end="00:00:02.000">Hi </span><span begin="00:00:02.000" end="00:00:03.000">there</span><span role="translation">Hola</span></p>
            </div></body></tt>"""
        val doc = LyricsParser.parse(raw)
        assertTrue(doc.isSynced)
        assertEquals(1_000L, doc.lines.single().timeMs)
        assertEquals("v1", doc.lines.single().voice)
        assertEquals("Hola", doc.lines.single().translation)
        assertTrue(doc.lines.single().words.isNotEmpty())
    }

    @Test fun malformedTtml_neverThrows() {
        val doc = LyricsParser.parse("<tt><body><p begin='bad'>text")
        assertFalse(doc.isSynced)
    }
}
