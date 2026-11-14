package dev.anilbeesetti.nextplayer.feature.player

import dev.anilbeesetti.nextplayer.core.model.ArtworkStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackArtworkStyleTest {
    @Test
    fun debugStylesUseOriginalSquareOrVideoFrames() {
        assertEquals(
            1f,
            artworkAspectRatio(
                ArtworkStyle.AUDIO,
                debug = true,
            ),
            0f,
        )
        assertEquals(
            16f / 10f,
            artworkAspectRatio(
                ArtworkStyle.VIDEO,
                debug = true,
            ),
            0f,
        )
    }

    @Test
    fun releaseIgnoresDeveloperOverrides() {
        for (style in ArtworkStyle.entries) {
            assertEquals(
                16f / 10f,
                artworkAspectRatio(
                    style,
                    debug = false,
                ),
                0f,
            )
        }
    }
}
