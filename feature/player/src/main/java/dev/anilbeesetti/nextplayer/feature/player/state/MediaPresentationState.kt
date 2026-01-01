package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.annotation.IntRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.feature.player.extensions.formatted
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@UnstableApi
@Composable
fun rememberMediaPresentationState(player: Player): MediaPresentationState {
    val mediaPresentationState = remember { MediaPresentationState(player) }
    LaunchedEffect(player) { mediaPresentationState.observe() }
    return mediaPresentationState
}

@Stable
class MediaPresentationState(
    private val player: Player,
    @param:IntRange(from = 0) private val tickIntervalMs: Long = 500,
) {
    var position: Long by mutableLongStateOf(0L)
        private set

    var duration: Long by mutableLongStateOf(0L)
        private set

    var isPlaying: Boolean by mutableStateOf(false)
        private set

    var isLoading: Boolean by mutableStateOf(true)
        private set

    var isBuffering: Boolean by mutableStateOf(false)
        private set

    suspend fun observe() {
        updatePosition()
        updateDuration()
        isPlaying = player.isPlaying
        isLoading = player.isLoading
        isBuffering = player.playbackState == Player.STATE_BUFFERING

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

                    if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                        this@MediaPresentationState.isBuffering = player.playbackState == Player.STATE_BUFFERING
                    }

                    if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                        this@MediaPresentationState.isPlaying = player.isPlaying
                    }

                    if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)) {
                        updatePosition()
                    }

                    if (events.containsAny(Player.EVENT_IS_LOADING_CHANGED)) {
                        this@MediaPresentationState.isLoading = player.isLoading
                    }
                }
            }

            while (true) {
                delay(tickIntervalMs)
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
