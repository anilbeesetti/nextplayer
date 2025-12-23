package dev.anilbeesetti.nextplayer.feature.player.buttons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberNextButtonState
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR

@Composable
internal fun NextButton(player: Player, modifier: Modifier = Modifier) {
    val state = rememberNextButtonState(player)

    PlayerButton(modifier = modifier, isEnabled = state.isEnabled, onClick = state::onClick) {
        Icon(
            painter = painterResource(coreUiR.drawable.ic_skip_next),
            contentDescription = stringResource(coreUiR.string.player_controls_next),
        )
    }
}
