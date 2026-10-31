package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize

@Composable
fun rememberSubtitlePositionState(
    savedOffsetY: Float,
    onPositionSaved: (Float) -> Unit,
): SubtitlePositionState {
    val state = remember {
        SubtitlePositionState(
            savedOffsetY,
            onPositionSaved,
        )
    }
    LaunchedEffect(savedOffsetY) { state.syncSavedOffset(savedOffsetY) }
    return state
}

/**
 * Holds how far up or down the user has dragged the subtitles from their default spot, as a
 * fraction of the player's height so the position holds across rotation and screen sizes.
 * Subtitles only move vertically; they stay centred horizontally.
 */
@Stable
class SubtitlePositionState(initialOffsetY: Float, private val onPositionSaved: (Float) -> Unit) {
    var offsetY by mutableStateOf(initialOffsetY)
        private set
    var isDragging by mutableStateOf(false)
        private set
    var isPressed by mutableStateOf(false)
        private set

    /** The subtitle shows a highlight from the first touch, so it is clear it can be moved. */
    val isHighlighted: Boolean get() = isPressed || isDragging
    var containerSize by mutableStateOf(IntSize.Zero)

    /**
     * Measures where the subtitle text is drawn, in the subtitle view's own coordinates (before
     * [offsetY] is applied). Null when nothing is drawn. Set by the view that renders the subtitles.
     */
    var textBoundsProvider: (() -> Rect?)? = null

    /** Whether [position] is on the subtitle text as drawn now, give or take [paddingPx]. */
    fun isOnSubtitle(position: Offset, paddingPx: Float): Boolean {
        val bounds = textBoundsProvider?.invoke() ?: return false
        return bounds
            .translate(
                0f,
                offsetY * containerSize.height,
            )
            .inflate(paddingPx)
            .contains(position)
    }

    fun onPressChange(pressed: Boolean) {
        isPressed = pressed
    }

    fun onDragStart() {
        isDragging = true
    }

    fun onDrag(deltaYPx: Float) {
        if (containerSize == IntSize.Zero) return
        offsetY = (offsetY + deltaYPx / containerSize.height).coerceIn(
            MIN_OFFSET_Y,
            MAX_OFFSET_Y,
        )
    }

    fun onDragEnd() {
        isDragging = false
        onPositionSaved(offsetY)
    }

    /** Applies the persisted value, unless the user is mid-drag. */
    fun syncSavedOffset(savedOffsetY: Float) {
        if (!isDragging) offsetY = savedOffsetY
    }

    companion object {
        const val MIN_OFFSET_Y = -0.85f
        const val MAX_OFFSET_Y = 0.05f
    }
}
