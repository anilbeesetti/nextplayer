package dev.anilbeesetti.nextplayer.feature.player.ui.controls

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.core.model.VideoContentScale
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.feature.player.buttons.LoopButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.PlayerButton
import dev.anilbeesetti.nextplayer.feature.player.extensions.drawableRes
import dev.anilbeesetti.nextplayer.feature.player.extensions.noRippleClickable
import dev.anilbeesetti.nextplayer.feature.player.extensions.setIsScrubbingModeEnabled
import dev.anilbeesetti.nextplayer.feature.player.state.MediaPresentationState
import dev.anilbeesetti.nextplayer.feature.player.state.durationFormatted
import dev.anilbeesetti.nextplayer.feature.player.state.pendingPositionFormatted
import dev.anilbeesetti.nextplayer.feature.player.state.positionFormatted
import dev.anilbeesetti.nextplayer.feature.player.state.rememberMediaPresentationState

@OptIn(UnstableApi::class)
@Composable
fun ControlsBottomView(
    modifier: Modifier = Modifier,
    player: Player,
    mediaPresentationState: MediaPresentationState,
    controlsAlignment: Alignment.Horizontal,
    videoContentScale: VideoContentScale,
    isPipSupported: Boolean,
    onVideoContentScaleClick: () -> Unit,
    onVideoContentScaleLongClick: () -> Unit,
    onLockControlsClick: () -> Unit,
    onPictureInPictureClick: () -> Unit,
    onRotateClick: () -> Unit,
    onPlayInBackgroundClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekEnd: () -> Unit,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var showPendingPosition by rememberSaveable { mutableStateOf(false) }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.noRippleClickable {
                    showPendingPosition = !showPendingPosition
                },
            ) {
                Text(
                    text = when (showPendingPosition) {
                        true -> "-${mediaPresentationState.pendingPositionFormatted}"
                        false -> mediaPresentationState.positionFormatted
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
                Text(
                    text = " / ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
                Text(
                    text = mediaPresentationState.durationFormatted,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            PlayerButton(
                modifier = modifier,
                onClick = onRotateClick,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_screen_rotation),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Slider(
            value = mediaPresentationState.position.toFloat(),
            valueRange = 0f..mediaPresentationState.duration.toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            onValueChangeFinished = { onSeekEnd() },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = controlsAlignment),
        ) {
            PlayerButton(onClick = onLockControlsClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_lock_open),
                    contentDescription = null,
                )
            }
            PlayerButton(
                onClick = onVideoContentScaleClick,
                onLongClick = onVideoContentScaleLongClick,
            ) {
                Icon(
                    painter = painterResource(videoContentScale.drawableRes()),
                    contentDescription = null,
                )
            }
            if (isPipSupported) {
                PlayerButton(onClick = onPictureInPictureClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pip),
                        contentDescription = null,
                    )
                }
            }
            PlayerButton(onClick = onPlayInBackgroundClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_headset),
                    contentDescription = null,
                )
            }
            LoopButton(player = player)
        }
    }
}