package dev.anilbeesetti.nextplayer.feature.player.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
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
import androidx.media3.common.Player
import dev.anilbeesetti.nextplayer.core.model.VideoContentScale
import dev.anilbeesetti.nextplayer.feature.player.extensions.noRippleClickable

@Composable
fun BoxScope.OverlayShowView(
    modifier: Modifier = Modifier,
    player: Player,
    overlayView: OverlayView?,
    videoContentScale: VideoContentScale,
    onDismiss: () -> Unit = {},
    onSelectSubtitleClick: () -> Unit = {},
    onVideoContentScaleChanged: (VideoContentScale) -> Unit = {},
) {
    Box(
        modifier = modifier
            .matchParentSize()
            .then(
                if (overlayView != null) {
                    Modifier.noRippleClickable(onClick = onDismiss)
                } else Modifier,
            ),
    )

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

            OverlayView.VIDEO_CONTENT_SCALE -> {
                VideoContentScaleSelectorView(
                    videoContentScale = videoContentScale,
                    onVideoContentScaleChanged = onVideoContentScaleChanged,
                    onDismiss = onDismiss,
                )
            }

            null -> {}
        }

        BackHandler {
            onDismiss()
        }
    }
}

val Configuration.isPortrait: Boolean
    get() = orientation == Configuration.ORIENTATION_PORTRAIT

enum class OverlayView {
    AUDIO_SELECTOR, SUBTITLE_SELECTOR, PLAYBACK_SPEED, VIDEO_CONTENT_SCALE
}