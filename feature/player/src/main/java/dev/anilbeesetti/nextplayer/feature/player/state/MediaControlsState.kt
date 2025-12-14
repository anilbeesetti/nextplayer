package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.feature.player.extensions.toggleSystemBars
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration

@UnstableApi
@Composable
fun rememberMediaControlsState(player: Player, hideAfter: Duration): MediaControlsState {
    val activity = LocalActivity.current
    val coroutineScope = rememberCoroutineScope()
    val mediaControlsState = remember { MediaControlsState(player, hideAfter, coroutineScope) }
    LaunchedEffect(player) { mediaControlsState.observe() }
    LaunchedEffect(mediaControlsState.controlsVisible) {
        if (mediaControlsState.controlsVisible) {
            activity?.toggleSystemBars(showBars = true)
        } else {
            activity?.toggleSystemBars(showBars = false)
        }
    }
    return mediaControlsState
}

@UnstableApi
class MediaControlsState(
    private val player: Player,
    private val hideAfter: Duration,
    private val scope: CoroutineScope,
) {
    private var autoHideControlsJob: Job? = null

    var controlsVisible: Boolean by mutableStateOf(true)
        private set

    fun showControls(duration: Duration = hideAfter) {
        controlsVisible = true
        autoHideControlsJob?.cancel()
        autoHideControlsJob = scope.launch {
            delay(duration)
            if (player.isPlaying) {
                controlsVisible = false
            }
        }
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

    suspend fun observe() {
        player.listen { events ->
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                if (player.isPlaying) {
                    showControls()
                }
            }
        }
    }
}
