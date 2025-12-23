package dev.anilbeesetti.nextplayer.feature.player.buttons

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR

@Composable
fun PlayPauseButton(player: Player, modifier: Modifier = Modifier) {
    val state = rememberPlayPauseButtonState(player)
    val icon = when (state.showPlay) {
        true -> painterResource(coreUiR.drawable.ic_play)
        false -> painterResource(coreUiR.drawable.ic_pause)
    }
    val contentDescription = when (state.showPlay) {
        true -> stringResource(coreUiR.string.play_pause)
        false -> stringResource(coreUiR.string.play_pause)
    }

    PlayerButton(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        isEnabled = state.isEnabled,
        onClick = state::onClick,
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(32.dp),
        )
    }
}
