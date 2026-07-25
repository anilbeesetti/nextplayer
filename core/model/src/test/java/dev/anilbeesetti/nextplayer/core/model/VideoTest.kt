package dev.anilbeesetti.nextplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoTest {

    @Test
    fun finishedVideoHasFullPlayedPercentage() {
        val video = Video.sample.copy(
            duration = 1_000L,
            playbackPosition = -1L,
        )

        assertEquals(1f, video.playedPercentage)
    }
}
