package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.media3.session.MediaController
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderServiceState
import dev.anilbeesetti.nextplayer.feature.player.service.setAudioDecoderMode
import dev.anilbeesetti.nextplayer.feature.player.service.setVideoDecoderMode
import dev.anilbeesetti.nextplayer.feature.player.service.tryDecoderFallback
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun rememberDecoderState(controller: MediaController, state: DecoderServiceState): DecoderState {
    val scope = rememberCoroutineScope()
    return remember(controller, state) { DecoderState(controller, scope, state) }
}

/** Decoder state arrives through MediaSession extras, including changes without player events. */
class DecoderState(
    private val controller: MediaController,
    private val scope: CoroutineScope,
    private val state: DecoderServiceState,
) {
    val videoMode get() = state.videoMode
    val audioMode get() = state.audioMode
    val recoveryState get() = state.recoveryState

    fun switchVideoTo(mode: DecoderMode) {
        scope.launch { controller.setVideoDecoderMode(mode) }
    }

    fun switchAudioTo(mode: DecoderMode) {
        scope.launch { controller.setAudioDecoderMode(mode) }
    }

    fun tryFallback() {
        scope.launch { controller.tryDecoderFallback() }
    }
}
