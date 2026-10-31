package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitlePositionStateTest {
    private val saved = mutableListOf<Offset>()

    private fun state(initial: Offset = Offset.Zero) = SubtitlePositionState(
        initialOffset = initial,
        onPositionSaved = { saved += it },
    ).apply { containerSize = IntSize(width = 1000, height = 500) }

    @Test
    fun `drag converts pixels to screen fractions`() {
        val state = state()

        state.onDrag(Offset(x = 100f, y = -50f))

        assertEquals(Offset(x = 0.1f, y = -0.1f), state.offset)
    }

    @Test
    fun `drag is clamped so the subtitle stays on screen`() {
        val state = state()

        state.onDrag(Offset(x = 5000f, y = -5000f))
        assertEquals(SubtitlePositionState.MAX_OFFSET_X, state.offset.x, 0f)
        assertEquals(SubtitlePositionState.MIN_OFFSET_Y, state.offset.y, 0f)

        state.onDrag(Offset(x = -50000f, y = 50000f))
        assertEquals(-SubtitlePositionState.MAX_OFFSET_X, state.offset.x, 0f)
        assertEquals(SubtitlePositionState.MAX_OFFSET_Y, state.offset.y, 0f)
    }

    @Test
    fun `drag end saves the final position once`() {
        val state = state()

        state.onDragStart()
        assertTrue(state.isDragging)
        state.onDrag(Offset(x = 100f, y = -50f))
        state.onDragEnd()

        assertFalse(state.isDragging)
        assertEquals(listOf(Offset(x = 0.1f, y = -0.1f)), saved)
    }

    @Test
    fun `drag zone follows the saved offset`() {
        val state = state()
        // Default band is the lower part of the screen.
        assertTrue(state.isInDragZone(Offset(x = 500f, y = 400f)))
        assertFalse(state.isInDragZone(Offset(x = 500f, y = 100f)))

        state.onDrag(Offset(x = 0f, y = -300f))

        assertTrue(state.isInDragZone(Offset(x = 500f, y = 100f)))
        assertFalse(state.isInDragZone(Offset(x = 500f, y = 400f)))
    }

    @Test
    fun `external offset updates are ignored while dragging`() {
        val state = state()
        state.onDragStart()
        state.onDrag(Offset(x = 100f, y = 0f))

        state.syncSavedOffset(Offset.Zero)

        assertEquals(Offset(x = 0.1f, y = 0f), state.offset)
    }

    @Test
    fun `external offset updates apply when idle`() {
        val state = state()

        state.syncSavedOffset(Offset(x = 0.2f, y = -0.3f))

        assertEquals(Offset(x = 0.2f, y = -0.3f), state.offset)
    }
}
