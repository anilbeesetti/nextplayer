package dev.anilbeesetti.nextplayer.feature.player.state

import io.github.anilbeesetti.nextlib.mediainfo.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChaptersStateTest {
    @Test
    fun chaptersAreSortedAndDeduplicatedWithinThePlayableDuration() {
        val chapters = listOf(-1L, 30_000L, 10_000L, 10_000L, 60_000L, Long.MAX_VALUE)
            .mapIndexed { index, start -> Chapter(index, start, start, null) }

        val playable = chapters.forDuration(60_000L)
        assertEquals(listOf(10_000L, 30_000L), playable.map { it.start })
        assertEquals(2, playable.first().index)
        assertEquals(-1, playable.currentChapterIndex(0L))
        assertEquals(0, playable.currentChapterIndex(10_000L))
        assertEquals(0, playable.currentChapterIndex(29_999L))
        assertEquals(1, playable.currentChapterIndex(30_000L))
        assertEquals(1, playable.currentChapterIndex(60_000L))
        assertEquals(0, playable.currentChapterIndex(15_000L))
        assertTrue(chapters.forDuration(0L).isEmpty())
        assertTrue(chapters.forDuration(-1L).isEmpty())
        assertEquals(-1, emptyList<Chapter>().currentChapterIndex(0L))
    }
}
