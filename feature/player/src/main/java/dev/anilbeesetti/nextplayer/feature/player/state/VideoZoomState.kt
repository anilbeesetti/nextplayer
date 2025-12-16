package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.core.model.VideoContentScale

@UnstableApi
@Composable
fun rememberVideoZoomState(initialContentScale: VideoContentScale): VideoZoomState {
    val videoZoomState = remember { VideoZoomState(initialContentScale) }
    return videoZoomState
}

class VideoZoomState(
    private val initialContentScale: VideoContentScale
) {
    var videoContentScale: VideoContentScale by mutableStateOf(initialContentScale)
        private set

    fun onVideoContentScaleChanged(newContentScale: VideoContentScale) {
        videoContentScale = newContentScale
    }
}