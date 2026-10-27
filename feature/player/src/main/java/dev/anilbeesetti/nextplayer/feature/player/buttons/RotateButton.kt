package dev.anilbeesetti.nextplayer.feature.player.buttons

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.feature.player.state.rememberRotationState

@Composable
fun RotateButton(modifier: Modifier = Modifier) {
    val rotationState = rememberRotationState()
    PlayerButton(
        modifier = modifier,
        enabled = rotationState != null,
        onClick = { rotationState?.rotate() },
        containerColor = PlayerButtonBlackAlpha,
        contentPadding = PaddingValues(8.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_screen_rotation),
            contentDescription = stringResource(R.string.screen_rotation),
            modifier = Modifier.size(20.dp),
        )
    }
}
