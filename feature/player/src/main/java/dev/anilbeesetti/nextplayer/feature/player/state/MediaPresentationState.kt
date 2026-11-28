package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.awaitCancellation

@UnstableApi
@Composable
fun rememberMediaPresentationState(player: Player?): MediaPresentationState {
    val mediaPresentationState = remember(player) { MediaPresentationState(player = player) }
    LaunchedEffect(key1 = player) { mediaPresentationState.observe() }
    return mediaPresentationState
}

@Stable
class MediaPresentationState(private val player: Player?) {
    var showBufferingIndicator: Boolean by mutableStateOf(value = false)
        private set

    suspend fun observe() {
        val player = player ?: return
        showBufferingIndicator = player.playbackState == Player.STATE_BUFFERING

        val listener = object : Player.Listener {
            private var isSeeking = false

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) isSeeking = true
            }

            override fun onEvents(player: Player, events: Player.Events) {
                val isBuffering = player.playbackState == Player.STATE_BUFFERING
                if (!isBuffering || events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                    isSeeking = false
                }
                showBufferingIndicator = isBuffering && !isSeeking
            }
        }
        player.addListener(listener)
        try {
            awaitCancellation()
        } finally {
            player.removeListener(listener)
        }
    }
}
