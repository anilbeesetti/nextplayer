package com.graviton.feature.player.ui.controls

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.graviton.core.model.DecoderMode
import com.graviton.core.ui.R
import com.graviton.core.ui.extensions.copy
import com.graviton.feature.player.buttons.PlayerButton
import com.graviton.feature.player.extensions.nameRes

/**
 * The top row of the video controls.
 *
 * Track and speed selection stay here as one-tap controls; everything else lives behind the
 * three-dot overflow so the bar does not grow without limit on small screens.
 */
@OptIn(UnstableApi::class)
@Composable
fun ControlsTopView(
    modifier: Modifier = Modifier,
    title: String,
    currentDecoderMode: DecoderMode = DecoderMode.AUTO,
    onAudioClick: () -> Unit = {},
    onSubtitleClick: () -> Unit = {},
    onPlaybackSpeedClick: () -> Unit = {},
    onDecoderClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onBackClick: () -> Unit,
) {
    val systemBarsPadding = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    // Add top spacing only when the system bars don't already provide it (e.g. on TV / landscape).
    val extraTopPadding = if (systemBarsPadding.calculateTopPadding() == 0.dp) 16.dp else 0.dp
    Row(
        modifier = modifier
            .padding(systemBarsPadding.copy(bottom = 0.dp))
            .padding(horizontal = 8.dp)
            .padding(bottom = 16.dp)
            .padding(top = extraTopPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlayerButton(onClick = onBackClick) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_left),
                contentDescription = stringResource(R.string.navigate_up),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AssistChip(
                onClick = onDecoderClick,
                label = { Text(text = stringResource(currentDecoderMode.nameRes())) },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = Color.White,
                ),
                border = AssistChipDefaults.assistChipBorder(
                    enabled = true,
                    borderColor = Color.White.copy(alpha = 0.5f),
                ),
            )
            PlayerButton(onClick = onPlaybackSpeedClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_speed),
                    contentDescription = stringResource(R.string.select_playback_speed),
                )
            }
            PlayerButton(onClick = onAudioClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_audio_track),
                    contentDescription = stringResource(R.string.select_audio_track),
                )
            }
            PlayerButton(onClick = onSubtitleClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_subtitle_track),
                    contentDescription = stringResource(R.string.select_subtitle_track),
                )
            }
            PlayerButton(onClick = onMoreClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.more_options),
                )
            }
        }
    }
}
