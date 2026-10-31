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
    savedOffset: Offset,
    onPositionSaved: (Offset) -> Unit,
): SubtitlePositionState {
    val state = remember {
        SubtitlePositionState(
            savedOffset,
            onPositionSaved,
        )
    }
    LaunchedEffect(savedOffset) { state.syncSavedOffset(savedOffset) }
    return state
}

/**
 * Holds how far the user has dragged the subtitles from their default spot, as fractions of the
 * player's width and height so the position holds across rotation and screen sizes.
 */
@Stable
class SubtitlePositionState(initialOffset: Offset, private val onPositionSaved: (Offset) -> Unit) {
    var offset by mutableStateOf(initialOffset)
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
     * [offset] is applied). Null when nothing is drawn. Set by the view that renders the subtitles.
     */
    var textBoundsProvider: (() -> Rect?)? = null

    /** Whether [position] is on the subtitle text as drawn now, give or take [paddingPx]. */
    fun isOnSubtitle(position: Offset, paddingPx: Float): Boolean {
        val bounds = textBoundsProvider?.invoke() ?: return false
        return bounds
            .translate(
                offset.x * containerSize.width,
                offset.y * containerSize.height,
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

    fun onDrag(deltaPx: Offset) {
        if (containerSize == IntSize.Zero) return
        offset = Offset(
            x = (offset.x + deltaPx.x / containerSize.width).coerceIn(
                -MAX_OFFSET_X,
                MAX_OFFSET_X,
            ),
            y = (offset.y + deltaPx.y / containerSize.height).coerceIn(
                MIN_OFFSET_Y,
                MAX_OFFSET_Y,
            ),
        )
    }

    fun onDragEnd() {
        isDragging = false
        onPositionSaved(offset)
    }

    /** Applies the persisted value, unless the user is mid-drag. */
    fun syncSavedOffset(saved: Offset) {
        if (!isDragging) offset = saved
    }

    companion object {
        const val MAX_OFFSET_X = 0.4f
        const val MIN_OFFSET_Y = -0.85f
        const val MAX_OFFSET_Y = 0.05f
    }
}
