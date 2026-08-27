package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController

@UnstableApi
@Composable
fun rememberErrorState(player: MediaController): ErrorState {
    val errorState = remember(player) { ErrorState(player) }
    LaunchedEffect(player) { errorState.observe() }
    return errorState
}

class ErrorState(
    private val player: MediaController,
) {
    var playbackError: PlaybackException? by mutableStateOf(player.playerError)
        private set

    fun dismiss() {
        playbackError = null
    }

    suspend fun observe() {
        playbackError = player.playerError
        player.listen { events ->
            if (
                events.contains(Player.EVENT_PLAYER_ERROR) ||
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)
            ) {
                playbackError = player.playerError
            }
        }
    }
}
