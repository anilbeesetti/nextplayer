package com.graviton.feature.player

import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.graviton.core.model.LongPressGesture
import com.graviton.core.ui.R
import com.graviton.feature.player.extensions.toContentScale
import com.graviton.feature.player.state.ControlsVisibilityState
import com.graviton.feature.player.state.LongPressSeekState
import com.graviton.feature.player.state.MediaContentType
import com.graviton.feature.player.state.PictureInPictureState
import com.graviton.feature.player.state.SeekGestureState
import com.graviton.feature.player.state.TapGestureState
import com.graviton.feature.player.state.VideoZoomAndContentScaleState
import com.graviton.feature.player.state.VolumeAndBrightnessGestureState
import com.graviton.feature.player.state.rememberMediaContentTypeState
import com.graviton.feature.player.state.rememberMetadataState
import com.graviton.feature.player.ui.PlayerGestures
import com.graviton.feature.player.ui.ShutterView
import com.graviton.feature.player.ui.SubtitleConfiguration
import com.graviton.feature.player.ui.SubtitleView

/**
 * Stable rendering host for the shared Media3 player.
 *
 * Two rules keep audio and video from leaking into each other:
 *
 * 1. [PlayerSurface] is composed unconditionally, so the player owns exactly one surface for the
 *    whole session. Swapping it in and out per media item is what produced detached buffers and
 *    "audio with a black screen" after an audio → video transition.
 * 2. Artwork is drawn *above* that surface, in the UI layer, and only while the current item is
 *    known to be audio-only. Artwork is never handed to the video renderer, so it cannot survive
 *    into the next video item.
 *
 * While the content type is still [MediaContentType.UNKNOWN] (the window between a media item
 * transition and the new item's tracks arriving) the shutter covers the surface, so neither a stale
 * frame nor the previous item's artwork is visible.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerContentFrame(
    modifier: Modifier = Modifier,
    player: Player,
    pictureInPictureState: PictureInPictureState,
    controlsVisibilityState: ControlsVisibilityState,
    tapGestureState: TapGestureState,
    longPressSeekState: LongPressSeekState,
    longPressAction: LongPressGesture,
    seekIncrementMs: Long,
    seekGestureState: SeekGestureState,
    videoZoomAndContentScaleState: VideoZoomAndContentScaleState,
    volumeAndBrightnessGestureState: VolumeAndBrightnessGestureState,
    subtitleConfiguration: SubtitleConfiguration,
) {
    val presentationState = rememberPresentationState(player)
    val contentTypeState = rememberMediaContentTypeState(player)
    val metadataState = rememberMetadataState(player)
    val contentType = contentTypeState.contentType

    val frameModifier = modifier
        .fillMaxSize()
        .onGloballyPositioned {
            val bounds = it.boundsInWindow()
            pictureInPictureState.setVideoViewRect(
                Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt()),
            )
        }

    Box(modifier = frameModifier.background(Color.Black)) {
        PlayerSurface(
            player = player,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier
                .fillMaxSize()
                .resizeWithContentScale(
                    contentScale = videoZoomAndContentScaleState.videoContentScale.toContentScale(),
                    sourceSizeDp = presentationState.videoSizeDp,
                )
                .graphicsLayer {
                    // Audio-only items must not present anything through the video surface, and a
                    // half-rendered frame during a transition must not be visible either.
                    alpha = if (contentType == MediaContentType.VIDEO) 1f else 0f
                    scaleX = videoZoomAndContentScaleState.zoom
                    scaleY = videoZoomAndContentScaleState.zoom
                    translationX = videoZoomAndContentScaleState.offset.x
                    translationY = videoZoomAndContentScaleState.offset.y
                },
        )

        // Artwork lives in the UI layer, above the surface, keyed by media id so a new item can
        // never keep showing the previous item's image.
        AnimatedVisibility(
            visible = contentType == MediaContentType.AUDIO_ONLY,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(metadataState.artworkUri)
                        .build(),
                    contentDescription = stringResource(R.string.audio_artwork_description),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(0.72f),
                )
            }
        }
    }

    PlayerGestures(
        controlsVisibilityState = controlsVisibilityState,
        tapGestureState = tapGestureState,
        longPressSeekState = longPressSeekState,
        longPressAction = longPressAction,
        seekIncrementMs = seekIncrementMs,
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

    val coverForVideo = contentType == MediaContentType.VIDEO && presentationState.coverSurface
    if (coverForVideo || contentType == MediaContentType.UNKNOWN) {
        ShutterView()
    }
}
