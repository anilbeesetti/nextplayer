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
import androidx.media3.ui.compose.state.rememberShuffleButtonState
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR
import dev.anilbeesetti.nextplayer.feature.player.LocalControlsVisibilityState

@OptIn(UnstableApi::class)
@Composable
fun ShuffleButton(player: Player, modifier: Modifier = Modifier) {
    val state = rememberShuffleButtonState(player)
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
            painter = shuffleModeIconPainter(state.shuffleOn),
            contentDescription = shuffleContentDescription(state.shuffleOn),
        )
    }
}

@Composable
private fun shuffleModeIconPainter(shuffleOn: Boolean): Painter {
    return when (shuffleOn) {
        true -> painterResource(coreUiR.drawable.ic_shuffle_on)
        false -> painterResource(coreUiR.drawable.ic_shuffle)
    }
}

@Composable
private fun shuffleContentDescription(shuffleOn: Boolean): String {
    return when (shuffleOn) {
        true -> stringResource(coreUiR.string.shuffle_on)
        false -> stringResource(coreUiR.string.shuffle_off)
    }
}
