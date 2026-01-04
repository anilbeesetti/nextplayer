package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.listen
import dev.anilbeesetti.nextplayer.feature.player.extensions.toggleSystemBars
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun rememberControlsVisibilityState(player: Player, hideAfter: Duration): ControlsVisibilityState {
    val activity = LocalActivity.current
    val coroutineScope = rememberCoroutineScope()
    val controlsVisibilityState = remember { ControlsVisibilityState(player, hideAfter, coroutineScope) }
    LaunchedEffect(player) { controlsVisibilityState.observe() }
    LaunchedEffect(controlsVisibilityState.controlsVisible, controlsVisibilityState.controlsLocked) {
        if (controlsVisibilityState.controlsLocked) {
            activity?.toggleSystemBars(showBars = false)
            return@LaunchedEffect
        }
        if (controlsVisibilityState.controlsVisible) {
            activity?.toggleSystemBars(showBars = true)
        } else {
            activity?.toggleSystemBars(showBars = false)
        }
    }
    return controlsVisibilityState
}

@Stable
class ControlsVisibilityState(
    private val player: Player,
    private val hideAfter: Duration,
    private val scope: CoroutineScope,
) {
    private var autoHideControlsJob: Job? = null

    var controlsVisible: Boolean by mutableStateOf(true)
        private set

    var controlsLocked: Boolean by mutableStateOf(false)
        private set

    fun showControls(duration: Duration = hideAfter) {
        controlsVisible = true
        autoHideControls(duration)
    }

    fun hideControls() {
        autoHideControlsJob?.cancel()
        controlsVisible = false
    }

    fun toggleControlsVisibility() {
        if (controlsVisible) {
            hideControls()
        } else {
            showControls()
        }
    }

    fun lockControls() {
        controlsLocked = true
    }

    fun unlockControls() {
        controlsLocked = false
        showControls()
    }

    suspend fun observe() {
        player.listen { events ->
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                if (player.isPlaying) {
                    autoHideControls()
                }
            }
        }
    }

    private fun autoHideControls(duration: Duration = hideAfter) {
        autoHideControlsJob?.cancel()
        autoHideControlsJob = scope.launch {
            delay(duration)
            if (player.isPlaying) {
                controlsVisible = false
            }
        }
    }
}
