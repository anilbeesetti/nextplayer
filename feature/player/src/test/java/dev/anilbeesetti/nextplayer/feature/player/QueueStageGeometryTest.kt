package dev.anilbeesetti.nextplayer.feature.player

import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun onlyShorterScreensTightenTheControlsForTheQueue() {
        fun stages(height: Float) = QueueStageGeometry(
            width = 1080f,
            height = height,
            topInset = 130f,
            blockHeight = 700f,
            density = Density(2.75f),
        )
        val tall = stages(3200f)
        assertEquals(
            0f,
            tall.transportLift + tall.transportCut,
            0f,
        )
        val short = stages(2300f)
        assertTrue(short.seekLift > 0f && short.transportLift > short.seekLift)
        assertTrue(short.transportCut > 0f)
    }

    @Test
    fun scrollingTheListNeverRaisesThePanelPastItsStage() {
        val sheet = PlaybackQueueSheet(CoroutineScope(Dispatchers.Unconfined))
        sheet.setAnchors(
            closed = 2000f,
            half = 1200f,
            full = 200f,
        )
        sheet.snapTo(QueueStage.HALF)
        assertEquals(
            0f,
            sheet.raiseBy(-300f),
            0f,
        )
        // A pull down past the list's top can be scrolled back, but only to the half stage.
        sheet.dragBy(200f)
        assertEquals(
            -200f,
            sheet.raiseBy(-500f),
            0f,
        )
        assertEquals(
            1200f,
            sheet.panelTop,
            0f,
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
