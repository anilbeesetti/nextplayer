package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import dev.anilbeesetti.nextplayer.feature.player.service.getSkipSilenceEnabled
import dev.anilbeesetti.nextplayer.feature.player.service.setSkipSilenceEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@UnstableApi
@Composable
fun rememberPlaybackParametersState(player: Player): PlaybackParametersState {
    val scope = rememberCoroutineScope()
    val playbackParametersState = remember { PlaybackParametersState(player, scope) }
    LaunchedEffect(player) { playbackParametersState.observe() }
    return playbackParametersState
}

@UnstableApi
class PlaybackParametersState(
    private val player: Player,
    private val scope: CoroutineScope,
) {
    var speed: Float by mutableFloatStateOf(1f)
        private set

    var skipSilenceEnabled: Boolean by mutableStateOf(false)
        private set

    fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
    }

    fun setIsSkipSilenceEnabled(enabled: Boolean) {
        scope.launch {
            when (player) {
                is MediaController -> player.setSkipSilenceEnabled(enabled)
                is ExoPlayer -> player.skipSilenceEnabled = enabled
                else -> return@launch
            }
            updateSkipSilenceEnabled()
        }
    }

    suspend fun observe() {
        updateSpeed()
        updateSkipSilenceEnabled()

        player.listen { events ->
            if (events.contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)) {
                updateSpeed()
            }
        }
    }

    private fun updateSpeed() {
        speed = player.playbackParameters.speed
    }

    private fun updateSkipSilenceEnabled() {
        scope.launch {
            skipSilenceEnabled = when (player) {
                is MediaController -> player.getSkipSilenceEnabled()
                is ExoPlayer -> player.skipSilenceEnabled
                else -> return@launch
            }
        }
    }
}
