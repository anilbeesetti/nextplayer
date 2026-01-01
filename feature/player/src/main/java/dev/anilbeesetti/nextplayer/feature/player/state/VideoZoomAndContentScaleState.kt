package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Constraints
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.core.model.VideoContentScale
import dev.anilbeesetti.nextplayer.feature.player.extensions.copy
import dev.anilbeesetti.nextplayer.feature.player.extensions.next
import dev.anilbeesetti.nextplayer.feature.player.extensions.videoZoom
import kotlin.math.abs

@UnstableApi
@Composable
fun rememberVideoZoomAndContentScaleState(
    player: Player,
    initialContentScale: VideoContentScale,
    onEvent: (VideoZoomEvent) -> Unit = {},
): VideoZoomAndContentScaleState {
    val videoZoomAndContentScaleState = remember { VideoZoomAndContentScaleState(player, initialContentScale, onEvent) }
    LaunchedEffect(player) { videoZoomAndContentScaleState.observe() }
    return videoZoomAndContentScaleState
}

@Stable
class VideoZoomAndContentScaleState(
    private val player: Player,
    initialContentScale: VideoContentScale,
    private val onEvent: (VideoZoomEvent) -> Unit,
) {
    companion object Companion {
        private const val MIN_ZOOM = 0.25f
        private const val MAX_ZOOM = 4f
    }

    var videoContentScale: VideoContentScale by mutableStateOf(initialContentScale)
        private set

    var zoom: Float by mutableFloatStateOf(1f)
        private set

    var offset: Offset by mutableStateOf(Offset.Zero)
        private set

    var isZooming: Boolean by mutableStateOf(false)
        private set

    fun onVideoContentScaleChanged(newContentScale: VideoContentScale) {
        videoContentScale = newContentScale
        zoom = 1f
        offset = Offset.Zero
        onEvent(VideoZoomEvent.ContentScaleChanged(videoContentScale))
        updateVideoScaleMetadataAndSendEvent()
    }

    fun switchToNextVideoContentScale() {
        onVideoContentScaleChanged(videoContentScale.next())
    }

    fun onZoomPanGesture(constraints: Constraints, panChange: Offset, zoomChange: Float) {
        if (player.duration == C.TIME_UNSET) return

        isZooming = true
        zoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)

        val extraWidth = (zoom - 1) * constraints.maxWidth
        val extraHeight = (zoom - 1) * constraints.maxHeight

        val maxX = abs(extraWidth / 2)
        val maxY = abs(extraHeight / 2)

        // TODO: Add pan back
//        offset = Offset(
//            x = (offset.x + scale * panChange.x).coerceIn(-maxX, maxX),
//            y = (offset.y + scale * panChange.y).coerceIn(-maxY, maxY),
//        )
    }

    fun onZoomPanGestureEnd() {
        isZooming = false
        updateVideoScaleMetadataAndSendEvent()
    }

    suspend fun observe() {
        zoom = player.currentMediaItem?.mediaMetadata?.videoZoom ?: 1f
        player.listen { events ->
            if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) {
                zoom = player.currentMediaItem?.mediaMetadata?.videoZoom ?: 1f
            }
        }
    }

    private fun updateVideoScaleMetadataAndSendEvent(zoom: Float = this.zoom) {
        val currentMediaItem = player.currentMediaItem ?: return
        player.replaceMediaItem(
            player.currentMediaItemIndex,
            currentMediaItem.copy(videoZoom = zoom),
        )
        onEvent(VideoZoomEvent.ZoomChanged(currentMediaItem, zoom))
    }
}

sealed interface VideoZoomEvent {
    data class ContentScaleChanged(val contentScale: VideoContentScale) : VideoZoomEvent
    data class ZoomChanged(val mediaItem: MediaItem, val zoom: Float) : VideoZoomEvent
}
