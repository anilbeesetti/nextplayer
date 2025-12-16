package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.unit.Constraints
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.core.model.VideoContentScale
import kotlin.math.abs

@UnstableApi
@Composable
fun rememberVideoZoomState(initialContentScale: VideoContentScale): VideoZoomState {
    val videoZoomState = remember { VideoZoomState(initialContentScale) }
    return videoZoomState
}

class VideoZoomState(
    private val initialContentScale: VideoContentScale
) {
    companion object {
        private const val MIN_ZOOM = 0.25f
        private const val MAX_ZOOM = 4f
    }


    var videoContentScale: VideoContentScale by mutableStateOf(initialContentScale)
        private set

    var scale: Float by mutableFloatStateOf(1f)
        private set

    var offset: Offset by mutableStateOf(Offset.Zero)
        private set

    fun onVideoContentScaleChanged(newContentScale: VideoContentScale) {
        videoContentScale = newContentScale
    }

    fun onZoomPanGesture(constraints: Constraints, panChange: Offset, zoomChange: Float, changes: List<PointerInputChange>) {
        if (changes.count { it.pressed } != 2) return
        scale = (scale * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)

        val extraWidth = (scale - 1) * constraints.maxWidth
        val extraHeight = (scale - 1) * constraints.maxHeight

        val maxX = abs(extraWidth / 2)
        val maxY = abs(extraHeight / 2)

        offset = Offset(
            x = (offset.x + scale * panChange.x).coerceIn(-maxX, maxX),
            y = (offset.y + scale * panChange.y).coerceIn(-maxY, maxY),
        )

        changes.forEach { it.consume() }
    }
}