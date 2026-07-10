package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
    LaunchedEffect(controller) { state.sync() }
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
        if (newMode == mode) return
        scope.launch {
            if (controller.setDecoderMode(newMode)) {
                mode = newMode
            }
        }
    }

    suspend fun sync() {
        mode = controller.getDecoderMode() ?: mode
    }
}
