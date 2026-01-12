package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import dev.anilbeesetti.nextplayer.feature.player.service.getSubtitleDelayMilliseconds
import dev.anilbeesetti.nextplayer.feature.player.service.getSubtitleSpeed
import dev.anilbeesetti.nextplayer.feature.player.service.setSubtitleDelayMilliseconds
import dev.anilbeesetti.nextplayer.feature.player.service.setSubtitleSpeed
import io.github.anilbeesetti.nextlib.media3ext.rendrer.subtitleDelayMilliseconds
import io.github.anilbeesetti.nextlib.media3ext.rendrer.subtitleSpeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@UnstableApi
@Composable
fun rememberSubtitleOptionsState(player: Player): SubtitleOptionsState {
    val scope = rememberCoroutineScope()
    val subtitleOptionsState = remember { SubtitleOptionsState(player, scope) }
    LaunchedEffect(player) { subtitleOptionsState.observe() }
    return subtitleOptionsState
}

@Stable
class SubtitleOptionsState(
    val player: Player,
    val scope: CoroutineScope
) {

    var delayMilliseconds: Long by mutableLongStateOf(0L)
        private set

    var speedMultiplier: Float by mutableFloatStateOf(1f)
        private set


    fun setDelay(delayMillis: Long) {
        scope.launch {
            when (player) {
                is MediaController -> player.setSubtitleDelayMilliseconds(delayMillis)
                is ExoPlayer -> player.subtitleDelayMilliseconds = delayMillis
                else -> return@launch
            }
            updateSubtitleDelayMilliseconds()
        }
    }

    fun setSpeed(speed: Float) {
        scope.launch {
            when (player) {
                is MediaController -> player.setSubtitleSpeed(speed)
                is ExoPlayer -> player.subtitleSpeed = speed
                else -> return@launch
            }
            updateSubtitleSpeed()
        }
    }


    suspend fun observe() {
        updateSubtitleDelayMilliseconds()
        updateSubtitleSpeed()
        player.listen { events ->
            if (events.containsAny(Player.EVENT_TRACKS_CHANGED, Player.EVENT_CUES)) {
                updateSubtitleDelayMilliseconds()
                updateSubtitleSpeed()
            }
        }
    }

    private fun updateSubtitleDelayMilliseconds() {
        scope.launch {
            delayMilliseconds = when (player) {
                is MediaController -> player.getSubtitleDelayMilliseconds()
                is ExoPlayer -> player.subtitleDelayMilliseconds
                else -> return@launch
            }
        }
    }

    private fun updateSubtitleSpeed() {
        scope.launch {
            speedMultiplier = when (player) {
                is MediaController -> player.getSubtitleSpeed()
                is ExoPlayer -> player.subtitleSpeed
                else -> return@launch
            }
        }
    }
}