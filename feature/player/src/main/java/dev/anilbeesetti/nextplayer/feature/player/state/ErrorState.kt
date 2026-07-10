package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderMode
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderRecoveryStatus
import dev.anilbeesetti.nextplayer.feature.player.service.getDecoderRecoveryState
import dev.anilbeesetti.nextplayer.feature.player.service.tryDecoderFallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@UnstableApi
@Composable
fun rememberErrorState(player: Player): ErrorState {
    val scope = rememberCoroutineScope()
    val errorState = remember(player) { ErrorState(player, scope) }
    LaunchedEffect(player) { errorState.observe() }
    return errorState
}

class ErrorState(
    private val player: Player,
    private val scope: CoroutineScope,
) {
    var error: PlaybackException? by mutableStateOf(null)
        private set

    var unsupportedDecoderMode: DecoderMode? by mutableStateOf(null)
        private set

    fun dismiss() {
        error = null
    }

    fun tryDecoderFallback() {
        val controller = player as? MediaController ?: return
        unsupportedDecoderMode = null
        scope.launch {
            val fallbackStarted = runCatching { controller.tryDecoderFallback() }.getOrDefault(false)
            if (!fallbackStarted) {
                updateError()
            }
        }
    }

    suspend fun observe() {
        updateError()
        player.listen { events ->
            if (events.contains(Player.EVENT_PLAYER_ERROR)) {
                scope.launch { updateError() }
            }
            if (
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) &&
                player.playbackState == Player.STATE_READY
            ) {
                error = null
                unsupportedDecoderMode = null
            }
        }
    }

    private suspend fun updateError() {
        val playbackError = player.playerError
        if (playbackError == null) {
            error = null
            unsupportedDecoderMode = null
            return
        }

        val recoveryState = (player as? MediaController)?.let { controller ->
            runCatching { controller.getDecoderRecoveryState() }.getOrNull()
        }
        when (recoveryState?.status) {
            DecoderRecoveryStatus.RECOVERING -> {
                error = null
                unsupportedDecoderMode = null
            }

            DecoderRecoveryStatus.AWAITING_CONFIRMATION -> {
                val unsupportedMode = recoveryState.unsupportedMode
                error = playbackError.takeIf { unsupportedMode == null }
                unsupportedDecoderMode = unsupportedMode
            }

            DecoderRecoveryStatus.NONE,
            null,
            -> {
                unsupportedDecoderMode = null
                error = playbackError
            }
        }
    }
}
