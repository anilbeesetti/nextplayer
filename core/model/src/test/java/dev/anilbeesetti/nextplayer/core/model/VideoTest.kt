package dev.anilbeesetti.nextplayer.core.model

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTest {

    private val now = 1_700_000_000_000L
    private val oneDayMillis = 24 * 60 * 60 * 1000L

    @Test
    fun finishedVideoHasFullPlayedPercentage() {
        val video = Video.sample.copy(
            duration = 1_000L,
            playbackPosition = -1L,
        )

        assertEquals(1f, video.playedPercentage)
    }

    @Test
    fun unwatchedVideoAddedRecentlyIsNew() {
        val video = Video.sample.copy(
            dateModified = (now - 3 * oneDayMillis) / 1000L,
            lastPlayedAt = null,
        )

        assertTrue(video.isNew(nowMillis = now))
    }

    @Test
    fun unwatchedVideoAddedOverAWeekAgoIsNotNew() {
        val video = Video.sample.copy(
            dateModified = (now - 8 * oneDayMillis) / 1000L,
            lastPlayedAt = null,
        )

        assertFalse(video.isNew(nowMillis = now))
    }

    @Test
    fun watchedVideoIsNeverNewEvenIfRecentlyAdded() {
        val video = Video.sample.copy(
            dateModified = now / 1000L,
            lastPlayedAt = Date(now),
        )

        assertFalse(video.isNew(nowMillis = now))
    }
}
