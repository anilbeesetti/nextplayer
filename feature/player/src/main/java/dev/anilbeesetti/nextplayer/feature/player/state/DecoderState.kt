package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.session.MediaController
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderMode
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import dev.anilbeesetti.nextplayer.feature.player.service.getDecoderMode
import dev.anilbeesetti.nextplayer.feature.player.service.setDecoderMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun rememberDecoderState(
    controller: MediaController,
    initialMode: DecoderMode,
): DecoderState {
    val scope = rememberCoroutineScope()
    val state = remember(controller) { DecoderState(controller, initialMode, scope) }
    LaunchedEffect(controller) { state.observe() }
    return state
}

/** Keeps Compose state synchronized with the decoder mode owned by [PlayerService]. */
class DecoderState(
    private val controller: MediaController,
    initialMode: DecoderMode,
    private val scope: CoroutineScope,
) {
    var mode: DecoderMode by mutableStateOf(initialMode)
        private set

    fun switchTo(newMode: DecoderMode) {
        scope.launch {
            if (controller.setDecoderMode(newMode)) {
                mode = newMode
            }
        }
    }

    suspend fun observe() {
        sync()
        controller.listen { events ->
            if (
                events.contains(Player.EVENT_PLAYER_ERROR) ||
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
            ) {
                scope.launch { sync() }
            }
        }
    }

    private suspend fun sync() {
        mode = controller.getDecoderMode() ?: mode
    }
}
