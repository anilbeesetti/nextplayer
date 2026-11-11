package dev.anilbeesetti.nextplayer.feature.player

import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueStageGeometryTest {
    @Test
    fun halfArtworkFillsThePhoneWidth() {
        // A phone whose player is shorter than the emulator's still gets the full-width square.
        val stages = QueueStageGeometry(
            width = 1080f,
            height = 2300f,
            topInset = 130f,
            blockHeight = 700f,
            density = Density(2.75f),
        )
        assertEquals(
            1080f,
            stages.halfArtwork.width,
            .5f,
        )
    }

    @Test
    fun halfArtworkStaysSquareOnTallAndShortScreens() {
        for (height in listOf(
            2400f,
            1400f,
        )) {
            val stages = QueueStageGeometry(
                width = 1080f,
                height = height,
                topInset = 120f,
                blockHeight = 520f,
                density = Density(2.75f),
            )
            val half = stages.halfArtwork
            assertEquals(
                "height $height",
                half.width,
                half.height,
                .5f,
            )
            assertEquals(
                stages.thumbnail.width,
                stages.thumbnail.height,
                .5f,
            )
        }
    }
}
