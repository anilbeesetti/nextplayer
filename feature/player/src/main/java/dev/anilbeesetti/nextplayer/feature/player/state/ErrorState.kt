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

@UnstableApi
@Composable
fun rememberErrorState(player: Player): ErrorState {
    val errorState = remember { ErrorState(player) }
    LaunchedEffect(player) { errorState.observe() }
    return errorState
}

class ErrorState(
    private val player: Player,
) {
    var error: PlaybackException? by mutableStateOf(null)
        private set

    fun dismiss() {
        error = null
    }

    suspend fun observe() {
        error = player.playerError
        player.listen { events ->
            if (events.contains(Player.EVENT_PLAYER_ERROR)) {
                error = player.playerError
            }
        }
    }
}
