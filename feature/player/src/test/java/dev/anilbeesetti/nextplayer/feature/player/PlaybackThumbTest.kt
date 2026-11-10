package dev.anilbeesetti.nextplayer.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackThumbTest {
    @Test fun thumbStaysInsideBothEndsAndMirrorsInRtl() {
        for (width in listOf(
            0f,
            8f,
            200f,
        )) {
            for (thumb in listOf(
                6f,
                16f,
            )) {
                for (progress in listOf(
                    0f,
                    .25f,
                    1f,
                )) {
                    val ltr = playbackThumbCenter(
                        progress,
                        width,
                        thumb,
                        rtl = false,
                    )
                    val rtl = playbackThumbCenter(
                        progress,
                        width,
                        thumb,
                        rtl = true,
                    )
                    val inset = (thumb / 2).coerceAtMost(width / 2)
                    assertTrue(ltr >= inset && ltr <= width - inset)
                    assertTrue(rtl >= inset && rtl <= width - inset)
                    assertEquals(
                        width,
                        ltr + rtl,
                        .001f,
                    )
                }
            }
        }
        assertEquals(
            8f,
            playbackThumbCenter(
                0f,
                200f,
                16f,
                false,
            ),
            .001f,
        )
        assertEquals(
            192f,
            playbackThumbCenter(
                1f,
                200f,
                16f,
                false,
            ),
            .001f,
        )
    }
}
