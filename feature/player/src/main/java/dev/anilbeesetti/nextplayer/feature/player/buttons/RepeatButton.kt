package dev.anilbeesetti.nextplayer.feature.player.buttons

import androidx.annotation.OptIn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberRepeatButtonState
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR
import dev.anilbeesetti.nextplayer.feature.player.LocalControlsVisibilityState

@OptIn(UnstableApi::class)
@Composable
fun LoopButton(player: Player, modifier: Modifier = Modifier) {
    val state = rememberRepeatButtonState(player)
    val controlsVisibilityState = LocalControlsVisibilityState.current

    PlayerButton(
        modifier = modifier,
        isEnabled = state.isEnabled,
        onClick = {
            state.onClick()
            controlsVisibilityState?.showControls()
        },
    ) {
        Icon(
            painter = repeatModeIconPainter(state.repeatModeState),
            contentDescription = repeatModeContentDescription(state.repeatModeState),
        )
    }
}

@Composable
private fun repeatModeIconPainter(repeatMode: @Player.RepeatMode Int): Painter {
    return when (repeatMode) {
        Player.REPEAT_MODE_OFF -> painterResource(coreUiR.drawable.ic_loop_off)
        Player.REPEAT_MODE_ONE -> painterResource(coreUiR.drawable.ic_loop_one)
        else -> painterResource(coreUiR.drawable.ic_loop_all)
    }
}

@Composable
private fun repeatModeContentDescription(repeatMode: @Player.RepeatMode Int): String {
    return when (repeatMode) {
        Player.REPEAT_MODE_OFF -> stringResource(coreUiR.string.loop_mode_off)
        Player.REPEAT_MODE_ONE -> stringResource(coreUiR.string.loop_mode_one)
        else -> stringResource(coreUiR.string.loop_mode_all)
    }
}
