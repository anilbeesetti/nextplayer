package dev.anilbeesetti.nextplayer.feature.player

import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueStageGeometryTest {
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
