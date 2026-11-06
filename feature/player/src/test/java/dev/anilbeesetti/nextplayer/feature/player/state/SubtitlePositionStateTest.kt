package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitlePositionStateTest {
    private val saved = mutableListOf<Float>()

    private fun state(initialOffsetY: Float = 0f) = SubtitlePositionState(
        initialOffsetY = initialOffsetY,
        onPositionSaved = { saved += it },
    ).apply {
        containerSize = IntSize(
            width = 1000,
            height = 500,
        )
        textBounds = Rect(
            400f,
            350f,
            600f,
            400f,
        )
    }

    @Test
    fun `drag converts pixels to a fraction of the height`() {
        val state = state()

        state.onDrag(-50f)

        assertEquals(
            -0.1f,
            state.offsetY,
            0f,
        )
    }

    @Test
    fun `drag is clamped so the subtitle stays on screen`() {
        val state = state()

        state.onDrag(-5000f)
        assertEquals(
            -0.7f,
            state.offsetY,
            0f,
        )

        state.onDrag(50000f)
        assertEquals(
            0.2f,
            state.offsetY,
            0f,
        )
    }

    @Test
    fun `drag end saves the final position once`() {
        val state = state()

        state.onDragStart()
        assertTrue(state.isDragging)
        state.onDrag(-50f)
        state.onDragEnd()

        assertFalse(state.isDragging)
        assertEquals(
            listOf(-0.1f),
            saved,
        )
    }

    @Test
    fun `touch only counts on the drawn subtitle text`() {
        val state = state()
        // Text drawn at x 400..600, y 350..400 (container is 1000 x 500).
        state.textBounds = Rect(
            400f,
            350f,
            600f,
            400f,
        )

        assertTrue(
            state.isOnSubtitle(
                Offset(
                    500f,
                    375f,
                ),
                paddingPx = 0f,
            ),
        )
        assertFalse(
            state.isOnSubtitle(
                Offset(
                    500f,
                    300f,
                ),
                paddingPx = 0f,
            ),
        )
        assertFalse(
            state.isOnSubtitle(
                Offset(
                    100f,
                    375f,
                ),
                paddingPx = 0f,
            ),
        )
        // Padding gives a fingertip some reach around the text.
        assertTrue(
            state.isOnSubtitle(
                Offset(
                    500f,
                    330f,
                ),
                paddingPx = 30f,
            ),
        )
        assertFalse(
            state.isOnSubtitle(
                Offset(
                    500f,
                    300f,
                ),
                paddingPx = 30f,
            ),
        )
    }

    @Test
    fun `hit area follows the saved offset`() {
        val state = state()
        state.textBounds = Rect(
            400f,
            350f,
            600f,
            400f,
        )

        // Moved 300px up, as the view is translated on screen. Horizontal position is unchanged.
        state.onDrag(-300f)

        assertTrue(
            state.isOnSubtitle(
                Offset(
                    500f,
                    75f,
                ),
                paddingPx = 0f,
            ),
        )
        assertFalse(
            state.isOnSubtitle(
                Offset(
                    500f,
                    375f,
                ),
                paddingPx = 0f,
            ),
        )
    }

    @Test
    fun `nothing is hit when no text is drawn`() {
        val state = state().apply { textBounds = null }
        assertFalse(
            state.isOnSubtitle(
                Offset(
                    500f,
                    375f,
                ),
                paddingPx = 50f,
            ),
        )

        state.textBounds = null
        assertFalse(
            state.isOnSubtitle(
                Offset(
                    500f,
                    375f,
                ),
                paddingPx = 50f,
            ),
        )
    }

    @Test
    fun `highlight shows from the first touch and until the drag ends`() {
        val state = state()
        assertFalse(state.isHighlighted)

        state.onPressChange(true)
        assertTrue(state.isHighlighted)

        state.onDragStart()
        state.onPressChange(false)
        assertTrue(state.isHighlighted)

        state.onDragEnd()
        assertFalse(state.isHighlighted)
    }

    @Test
    fun `external offset updates are ignored while dragging`() {
        val state = state()
        state.onDragStart()
        state.onDrag(-50f)

        state.syncSavedOffset(0f)

        assertEquals(
            -0.1f,
            state.offsetY,
            0f,
        )
    }

    @Test
    fun `saved position stays visible after cue and size changes`() {
        val state = state(initialOffsetY = -0.7f)
        state.textBounds = Rect(
            400f,
            200f,
            600f,
            400f,
        )
        assertEquals(
            -200f,
            state.translationYPx,
            0f,
        )
        state.containerSize = IntSize(
            500,
            250,
        )
        state.textBounds = Rect(
            100f,
            80f,
            400f,
            230f,
        )
        assertEquals(
            -80f,
            state.translationYPx,
            0f,
        )
        assertTrue(
            state.isOnSubtitle(
                Offset(
                    200f,
                    20f,
                ),
                0f,
            ),
        )
        assertEquals(
            -0.7f,
            state.offsetY,
            0f,
        )
        state.onDrag(10f)
        assertEquals(
            -70f,
            state.translationYPx,
            0.001f,
        )
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `cancel restores position without saving`() {
        val state = state(initialOffsetY = -0.1f)
        state.onDragStart()
        state.onDrag(-50f)
        state.onDragCancel()
        assertFalse(state.isDragging)
        assertEquals(
            -0.1f,
            state.offsetY,
            0f,
        )
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `top positioned and oversized cues have safe bounds`() {
        val state = state()
        state.textBounds = Rect(
            0f,
            10f,
            100f,
            60f,
        )
        state.onDrag(-5000f)
        assertEquals(
            -10f,
            state.translationYPx,
            0f,
        )
        state.textBounds = Rect(
            0f,
            0f,
            100f,
            800f,
        )
        state.onDrag(5000f)
        assertEquals(
            0f,
            state.translationYPx,
            0f,
        )
    }

    @Test
    fun `external offset updates apply when idle`() {
        val state = state()

        state.syncSavedOffset(-0.3f)

        assertEquals(
            -0.3f,
            state.offsetY,
            0f,
        )
    }
}
