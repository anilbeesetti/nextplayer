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
fun rememberErrorState(player: MediaController): ErrorState {
    val scope = rememberCoroutineScope()
    val errorState = remember(player) { ErrorState(player, scope) }
    LaunchedEffect(player) { errorState.observe() }
    return errorState
}

class ErrorState(
    private val player: MediaController,
    private val scope: CoroutineScope,
) {
    var playbackError: PlaybackException? by mutableStateOf(null)
        private set

    var unsupportedDecoderMode: DecoderMode? by mutableStateOf(null)
        private set

    var allDecoderModesFailed: Boolean by mutableStateOf(false)
        private set

    val showPlayerError: Boolean
        get() = playbackError != null || allDecoderModesFailed

    fun dismiss() {
        playbackError = null
        allDecoderModesFailed = false
    }

    fun tryDecoderFallback() {
        unsupportedDecoderMode = null
        scope.launch {
            val fallbackStarted = runCatching { player.tryDecoderFallback() }.getOrDefault(false)
            if (!fallbackStarted) {
                sync()
            }
        }
    }

    suspend fun observe() {
        sync()
        player.listen { events ->
            if (
                events.contains(Player.EVENT_PLAYER_ERROR) ||
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                events.contains(Player.EVENT_TRACKS_CHANGED)
            ) {
                scope.launch { sync() }
            }
        }
    }

    private suspend fun sync() {
        val recoveryState = runCatching { player.getDecoderRecoveryState() }.getOrNull()
        when (recoveryState?.status) {
            DecoderRecoveryStatus.RECOVERING -> {
                playbackError = null
                unsupportedDecoderMode = null
                allDecoderModesFailed = false
            }

            DecoderRecoveryStatus.AWAITING_CONFIRMATION -> {
                playbackError = null
                unsupportedDecoderMode = recoveryState.unsupportedMode
                allDecoderModesFailed = false
            }

            DecoderRecoveryStatus.FAILED -> {
                playbackError = player.playerError
                unsupportedDecoderMode = null
                allDecoderModesFailed = true
            }

            DecoderRecoveryStatus.NONE,
            null,
            -> {
                playbackError = player.playerError
                unsupportedDecoderMode = null
                allDecoderModesFailed = false
            }
        }
    }
}
