package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.PlayerStateObserver
import androidx.media3.ui.compose.state.observeState

@UnstableApi
@Composable
fun rememberCuesState(player: Player?): CuesState {
    val cuesState = remember(player) { CuesState(player) }
    LaunchedEffect(player) { cuesState.observe() }
    return cuesState
}

@UnstableApi
class CuesState(
    private val player: Player?,
) {
    var currentCues: CueGroup? by mutableStateOf(null)
        private set

    private val playerStateObserver: PlayerStateObserver? =
        player?.observeState(Player.EVENT_CUES) {
            currentCues = player.currentCues
        }

    suspend fun observe() {
        currentCues = player?.currentCues
        playerStateObserver?.observe()
    }
}
