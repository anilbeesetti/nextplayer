package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi

@UnstableApi
@Composable
fun rememberMediaPresentationState(player: Player?): MediaPresentationState {
    val mediaPresentationState = remember(player) { MediaPresentationState(player) }
    LaunchedEffect(player) { mediaPresentationState.observe() }
    return mediaPresentationState
}

@Stable
class MediaPresentationState(
    private val player: Player?,
) {
    var isBuffering: Boolean by mutableStateOf(false)
        private set

    suspend fun observe() {
        isBuffering = player?.playbackState == Player.STATE_BUFFERING

        player?.listen { events ->
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                this@MediaPresentationState.isBuffering = player.playbackState == Player.STATE_BUFFERING
            }
        }
    }
}
