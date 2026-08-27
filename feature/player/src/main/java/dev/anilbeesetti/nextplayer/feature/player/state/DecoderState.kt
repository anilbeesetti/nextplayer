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
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderRecoveryState
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderRecoveryStatus
import dev.anilbeesetti.nextplayer.feature.player.service.getDecoderState
import dev.anilbeesetti.nextplayer.feature.player.service.setAudioDecoderMode
import dev.anilbeesetti.nextplayer.feature.player.service.setVideoDecoderMode
import dev.anilbeesetti.nextplayer.feature.player.service.tryDecoderFallback
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun rememberDecoderState(controller: MediaController): DecoderState {
    val scope = rememberCoroutineScope()
    val state = remember(controller) { DecoderState(controller, scope) }
    LaunchedEffect(controller) { state.observe() }
    return state
}

/** Mirrors the decoder modes and app-owned recovery state exposed by PlayerService. */
class DecoderState(
    private val controller: MediaController,
    private val scope: CoroutineScope,
) {
    var videoMode: DecoderMode? by mutableStateOf(null)
        private set

    var audioMode: DecoderMode? by mutableStateOf(null)
        private set

    var recoveryState: DecoderRecoveryState by mutableStateOf(DecoderRecoveryState())
        private set

    fun switchVideoTo(mode: DecoderMode) {
        scope.launch {
            if (runCatching { controller.setVideoDecoderMode(mode) }.getOrDefault(false)) sync()
        }
    }

    fun switchAudioTo(mode: DecoderMode) {
        scope.launch {
            if (runCatching { controller.setAudioDecoderMode(mode) }.getOrDefault(false)) sync()
        }
    }

    fun tryFallback() {
        recoveryState = recoveryState.copy(
            status = DecoderRecoveryStatus.RECOVERING,
            unsupportedMode = null,
        )
        scope.launch {
            runCatching { controller.tryDecoderFallback() }
            sync()
        }
    }

    suspend fun observe() {
        sync()
        controller.listen { events ->
            if (
                events.contains(Player.EVENT_PLAYER_ERROR) ||
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                events.contains(Player.EVENT_TRACKS_CHANGED) ||
                events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)
            ) {
                scope.launch { sync() }
            }
        }
    }

    private suspend fun sync() {
        val state = runCatching { controller.getDecoderState() }.getOrNull() ?: return
        videoMode = state.videoMode
        audioMode = state.audioMode
        recoveryState = state.recoveryState
    }
}
