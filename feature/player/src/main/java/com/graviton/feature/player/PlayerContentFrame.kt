package com.graviton.feature.player

import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import com.graviton.feature.player.extensions.toContentScale
import com.graviton.feature.player.state.ControlsVisibilityState
import com.graviton.feature.player.state.PictureInPictureState
import com.graviton.feature.player.state.SeekGestureState
import com.graviton.feature.player.state.TapGestureState
import com.graviton.feature.player.state.VideoZoomAndContentScaleState
import com.graviton.feature.player.state.VolumeAndBrightnessGestureState
import com.graviton.feature.player.ui.PlayerGestures
import com.graviton.feature.player.ui.ShutterView
import com.graviton.feature.player.ui.SubtitleConfiguration
import com.graviton.feature.player.ui.SubtitleView

@OptIn(UnstableApi::class)
@Composable
fun PlayerContentFrame(
    modifier: Modifier = Modifier,
    player: Player,
    pictureInPictureState: PictureInPictureState,
    controlsVisibilityState: ControlsVisibilityState,
    tapGestureState: TapGestureState,
    seekGestureState: SeekGestureState,
    videoZoomAndContentScaleState: VideoZoomAndContentScaleState,
    volumeAndBrightnessGestureState: VolumeAndBrightnessGestureState,
    subtitleConfiguration: SubtitleConfiguration,
) {
    val presentationState = rememberPresentationState(player)
    PlayerSurface(
        player = player,
        surfaceType = SURFACE_TYPE_SURFACE_VIEW,
        modifier = modifier
            .resizeWithContentScale(
                contentScale = videoZoomAndContentScaleState.videoContentScale.toContentScale(),
                sourceSizeDp = presentationState.videoSizeDp?.let { size ->
                    size.copy(
                        width = with(LocalDensity.current) { size.width.toDp().value },
                        height = with(LocalDensity.current) { size.height.toDp().value },
                    )
                },
            )
            .onGloballyPositioned {
                val bounds = it.boundsInWindow()
                val rect = Rect(
                    bounds.left.toInt(),
                    bounds.top.toInt(),
                    bounds.right.toInt(),
                    bounds.bottom.toInt(),
                )
                pictureInPictureState.setVideoViewRect(rect)
            }
            .graphicsLayer {
                scaleX = videoZoomAndContentScaleState.zoom
                scaleY = videoZoomAndContentScaleState.zoom
                translationX = videoZoomAndContentScaleState.offset.x
                translationY = videoZoomAndContentScaleState.offset.y
            },
    )

    PlayerGestures(
        controlsVisibilityState = controlsVisibilityState,
        tapGestureState = tapGestureState,
        pictureInPictureState = pictureInPictureState,
        seekGestureState = seekGestureState,
        videoZoomAndContentScaleState = videoZoomAndContentScaleState,
        volumeAndBrightnessGestureState = volumeAndBrightnessGestureState,
    )

    SubtitleView(
        player = player,
        isInPictureInPictureMode = pictureInPictureState.isInPictureInPictureMode,
        configuration = subtitleConfiguration,
    )

    if (presentationState.coverSurface) {
        ShutterView()
    }
}
