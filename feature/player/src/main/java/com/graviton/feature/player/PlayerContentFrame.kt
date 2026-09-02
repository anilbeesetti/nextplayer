package com.graviton.feature.player

import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.graviton.core.ui.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
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

/**
 * Stable rendering host for the shared Media3 player.
 *
 * SurfaceView is intentional here. A TextureView can keep a detached buffer after an Activity or
 * Compose AndroidView is recreated, which presents exactly as "audio with a black screen" on the
 * next open. PlayerSurface owns the view and reattaches it whenever the controller changes; this
 * function does not create a player and never releases one during recomposition.
 */
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
    val isAudio = player.mediaMetadata.artist != null &&
        player.currentTracks.groups.none { it.type == C.TRACK_TYPE_VIDEO }
    val frameModifier = modifier
        .fillMaxSize()
        .onGloballyPositioned {
            val bounds = it.boundsInWindow()
            pictureInPictureState.setVideoViewRect(
                Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt()),
            )
        }

    if (isAudio) {
        // The full player remains the same Media3 player UI for audio. Showing its artwork here
        // avoids a meaningless video surface while preserving the shared controls and session.
        Box(modifier = frameModifier.background(Color.Black), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(player.mediaMetadata.artworkUri)
                    .build(),
                contentDescription = stringResource(R.string.audio_artwork_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(0.72f),
            )
        }
    } else {
        PlayerSurface(
            player = player,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = frameModifier
                .resizeWithContentScale(
                    contentScale = videoZoomAndContentScaleState.videoContentScale.toContentScale(),
                    sourceSizeDp = presentationState.videoSizeDp,
                )
                .graphicsLayer {
                    scaleX = videoZoomAndContentScaleState.zoom
                    scaleY = videoZoomAndContentScaleState.zoom
                    translationX = videoZoomAndContentScaleState.offset.x
                    translationY = videoZoomAndContentScaleState.offset.y
                },
        )
    }

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

    if (presentationState.coverSurface && !isAudio) {
        ShutterView()
    }
}
