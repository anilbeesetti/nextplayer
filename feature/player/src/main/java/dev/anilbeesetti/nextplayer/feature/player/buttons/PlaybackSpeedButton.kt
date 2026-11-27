package dev.anilbeesetti.nextplayer.feature.player.buttons

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberPlaybackSpeedState
import dev.anilbeesetti.nextplayer.core.common.extensions.round
import dev.anilbeesetti.nextplayer.core.ui.R

@OptIn(UnstableApi::class)
@Composable
fun PlaybackSpeedButton(
    player: Player?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useBottomRowStyle: Boolean = false,
) {
    val state = rememberPlaybackSpeedState(player = player)
    val description = stringResource(R.string.select_playback_speed)
    val maxFontSize = if (useBottomRowStyle) 24.sp else MaterialTheme.typography.labelLarge.fontSize
    PlayerButton(
        modifier = modifier.semantics { contentDescription = description },
        enabled = state.isEnabled,
        onClick = onClick,
        containerColor = if (useBottomRowStyle) Color.Transparent else PlayerButtonBlackAlpha,
        contentPadding = PaddingValues(if (useBottomRowStyle) 2.dp else 4.dp),
    ) {
        Box(
            modifier = Modifier.size(if (useBottomRowStyle) 36.dp else 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = state.playbackSpeed.round(decimalPlaces = 2).toString(),
                style = MaterialTheme.typography.labelLarge.copy(
                    color = LocalContentColor.current,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    maxFontSize = maxFontSize,
                    minFontSize = 6.sp,
                    stepSize = 0.5.sp,
                ),
            )
        }
    }
}
