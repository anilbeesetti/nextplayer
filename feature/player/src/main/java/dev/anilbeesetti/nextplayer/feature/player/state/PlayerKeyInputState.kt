package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun rememberPlayerKeyInputState(
    player: Player,
    controls: ControlsVisibilityState,
    seekIncrementMs: Long,
): PlayerKeyInputState {
    val coroutineScope = rememberCoroutineScope()
    val state = remember(player, controls, seekIncrementMs) {
        PlayerKeyInputState(player, controls, seekIncrementMs, coroutineScope)
    }
    DisposableEffect(state) {
        onDispose { state.cancelPendingReset() }
    }
    return state
}

@Stable
internal class PlayerKeyInputState(
    private val player: Player,
    private val controls: ControlsVisibilityState,
    private val seekIncrementMs: Long,
    private val coroutineScope: CoroutineScope,
) {
    var isSeeking: Boolean by mutableStateOf(false)
        private set

    var seekOffsetMs: Long by mutableLongStateOf(0L)
        private set

    var seekPositionMs: Long by mutableLongStateOf(0L)
        private set

    private var resetJob: Job? = null

    @OptIn(UnstableApi::class)
    fun onKeyEvent(keyEvent: KeyEvent): Boolean {
        if (keyEvent.key == Key.Back && !controls.controlsLocked) {
            if (!controls.controlsVisible) return false // controls already hidden: let BACK exit
            if (keyEvent.type == KeyEventType.KeyUp) controls.hideControls()
            return true
        }
        if (keyEvent.type != KeyEventType.KeyDown) return false
        if (controls.controlsLocked) {
            return when (keyEvent.key) {
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                    if (controls.controlsVisible) controls.unlockControls() else controls.showControls()
                    true
                }

                else -> {
                    controls.showControls()
                    false
                }
            }
        }

        fun seekBy(deltaMs: Long) {
            val duration = player.duration
            val target = (player.currentPosition + deltaMs).coerceAtLeast(0)
            player.seekTo(if (duration > 0) target.coerceAtMost(duration) else target)
        }

        fun togglePlayPause() {
            if (player.isPlaying) player.pause() else player.play()
        }

        return when (keyEvent.key) {
            Key.MediaPlayPause, Key.Spacebar -> {
                togglePlayPause()
                controls.showControls()
                true
            }

            Key.MediaPlay -> {
                player.play()
                controls.showControls()
                true
            }

            Key.MediaPause -> {
                player.pause()
                controls.showControls()
                true
            }

            Key.MediaFastForward -> {
                seekBy(seekIncrementMs)
                controls.showControls()
                true
            }

            Key.MediaRewind -> {
                seekBy(-seekIncrementMs)
                controls.showControls()
                true
            }

            Key.MediaNext -> {
                player.seekToNext()
                controls.showControls()
                true
            }

            Key.MediaPrevious -> {
                player.seekToPrevious()
                controls.showControls()
                true
            }

            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                val controlsWereHidden = !controls.controlsVisible
                controls.showControls()
                controlsWereHidden
            }

            Key.DirectionLeft -> {
                if (!controls.controlsVisible) {
                    seekBy(-seekIncrementMs)
                    showSeekFeedback(-seekIncrementMs)
                    true
                } else {
                    controls.showControls()
                    false
                }
            }

            Key.DirectionRight -> {
                if (!controls.controlsVisible) {
                    seekBy(seekIncrementMs)
                    showSeekFeedback(seekIncrementMs)
                    true
                } else {
                    controls.showControls()
                    false
                }
            }

            Key.DirectionUp, Key.DirectionDown -> {
                if (!controls.controlsVisible) {
                    controls.showControls()
                    true
                } else {
                    controls.showControls()
                    false
                }
            }

            else -> false
        }
    }

    internal fun cancelPendingReset() {
        resetJob?.cancel()
        resetJob = null
    }

    private fun showSeekFeedback(deltaMs: Long) {
        if (!isSeeking) seekOffsetMs = 0L
        seekOffsetMs += deltaMs
        seekPositionMs = player.currentPosition
        isSeeking = true
        resetJob?.cancel()
        resetJob = coroutineScope.launch {
            delay(1.seconds)
            isSeeking = false
        }
    }
}
