package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.feature.player.extensions.formatted
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@UnstableApi
@Composable
fun rememberMediaPresentationState(player: Player): MediaPresentationState {
    val mediaPresentationState = remember { MediaPresentationState(player) }
    LaunchedEffect(player) { mediaPresentationState.observe() }
    return mediaPresentationState
}


class MediaPresentationState(
    private val player: Player,
) {
    var position: Long by mutableStateOf(0L)
        private set

    var duration: Long by mutableStateOf(0L)
        private set

    var isPlaying: Boolean by mutableStateOf(false)
        private set

    suspend fun observe() {
        position = player.currentPosition
        duration = player.duration.coerceAtLeast(0L)
        isPlaying = player.isPlaying

        coroutineScope {
            launch {
                player.listen { events ->
                    if (events.containsAny(
                            Player.EVENT_MEDIA_ITEM_TRANSITION,
                            Player.EVENT_TIMELINE_CHANGED,
                            Player.EVENT_PLAYBACK_STATE_CHANGED,
                        )
                    ) {
                        updateDuration()
                    }

                    if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED)) {
                        this@MediaPresentationState.isPlaying = player.isPlaying
                    }

                    if (events.containsAny(Player.EVENT_POSITION_DISCONTINUITY)) {
                        updatePosition()
                    }
                }
            }

            while (true) {
                delay(500)
                if (player.isPlaying) {
                    updatePosition()
                }
            }
        }
    }

    private fun updatePosition() {
        position = player.currentPosition.coerceAtLeast(0L)
    }

    private fun updateDuration() {
        duration = player.duration.coerceAtLeast(0L)
    }
}

val MediaPresentationState.positionFormatted: String
    get() = position.milliseconds.formatted()

val MediaPresentationState.durationFormatted: String
    get() = duration.milliseconds.formatted()

val MediaPresentationState.pendingPositionFormatted: String
    get() = (duration - position).milliseconds.formatted()
