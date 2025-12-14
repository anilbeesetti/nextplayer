package dev.anilbeesetti.nextplayer.feature.player.buttons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberPreviousButtonState
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR

@Composable
internal fun PreviousButton(player: Player, modifier: Modifier = Modifier) {
    val state = rememberPreviousButtonState(player)

    PlayerButton(modifier = modifier, onClick = state::onClick) {
        Icon(
            painter = painterResource(coreUiR.drawable.ic_skip_prev),
            contentDescription = stringResource(coreUiR.string.player_controls_previous),
        )
    }
}