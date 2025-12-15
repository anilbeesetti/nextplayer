package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.media3.session.MediaController
import dev.anilbeesetti.nextplayer.feature.player.OverlayView
import dev.anilbeesetti.nextplayer.feature.player.extensions.noRippleClickable
import dev.anilbeesetti.nextplayer.feature.player.isPortrait

@Composable
fun BoxScope.OverlayShowView(
    modifier: Modifier = Modifier,
    player: MediaController,
    overlayView: OverlayView?,
    onDismiss: () -> Unit = {},
    onSelectSubtitleClick: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .matchParentSize()
            .then(
                if (overlayView != null) {
                    Modifier.noRippleClickable(onClick = onDismiss)
                } else Modifier,
            ),
    ) {
        val configuration = LocalConfiguration.current

        AnimatedVisibility(
            modifier = Modifier.align(
                if (configuration.isPortrait) {
                    Alignment.BottomCenter
                } else {
                    Alignment.CenterEnd
                },
            ),
            visible = overlayView != null,
            enter = if (configuration.isPortrait) slideInVertically { it } else slideInHorizontally { it },
            exit = if (configuration.isPortrait) slideOutVertically { it } else slideOutHorizontally { it },
        ) {
            when (overlayView) {
                OverlayView.AUDIO_SELECTOR -> {
                    AudioTrackSelectorView(
                        player = player,
                        onDismiss = onDismiss,
                    )
                }

                OverlayView.SUBTITLE_SELECTOR -> {
                    SubtitleSelectorView(
                        player = player,
                        onSelectSubtitleClick = onSelectSubtitleClick,
                        onDismiss = onDismiss,
                    )
                }

                OverlayView.PLAYBACK_SPEED -> {
                    PlaybackSpeedSelectorView(player = player)
                }

                null -> {}
            }
        }
    }
}