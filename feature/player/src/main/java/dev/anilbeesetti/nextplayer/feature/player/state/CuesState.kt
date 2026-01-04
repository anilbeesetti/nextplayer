package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi

@UnstableApi
@Composable
fun rememberCuesState(player: Player): CuesState {
    val cuesState = remember { CuesState(player) }
    LaunchedEffect(player) { cuesState.observe() }
    return cuesState
}

class CuesState(
    private val player: Player,
) {
    var cues: List<Cue> by mutableStateOf(emptyList())
        private set

    suspend fun observe() {
        cues = player.currentCues.cues
        player.listen { events ->
            if (events.contains(Player.EVENT_CUES)) {
                cues = player.currentCues.cues
            }
        }
    }
}
