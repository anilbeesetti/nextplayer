package dev.anilbeesetti.nextplayer.feature.player

import android.content.res.Configuration
import android.graphics.Rect
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import dev.anilbeesetti.nextplayer.core.model.ControlButtonsPosition
import dev.anilbeesetti.nextplayer.core.model.VideoZoom
import dev.anilbeesetti.nextplayer.feature.player.buttons.LoopButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.NextButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.PlayPauseButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.PlayerButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.PreviousButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.RotationButton
import dev.anilbeesetti.nextplayer.feature.player.dialogs.videoZoomOptionsDialog
import dev.anilbeesetti.nextplayer.feature.player.extensions.next
import dev.anilbeesetti.nextplayer.feature.player.extensions.noRippleClickable
import dev.anilbeesetti.nextplayer.feature.player.extensions.setScrubbingModeEnabled
import dev.anilbeesetti.nextplayer.feature.player.extensions.shouldFastSeek
import dev.anilbeesetti.nextplayer.feature.player.state.durationFormatted
import dev.anilbeesetti.nextplayer.feature.player.state.pendingPositionFormatted
import dev.anilbeesetti.nextplayer.feature.player.state.positionFormatted
import dev.anilbeesetti.nextplayer.feature.player.state.rememberControlsVisibilityState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberDoubleTapGestureHandler
import dev.anilbeesetti.nextplayer.feature.player.state.rememberMediaPresentationState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberMetadataState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberPictureInPictureState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberSeekGestureState
import dev.anilbeesetti.nextplayer.feature.player.state.seekAmountFormatted
import dev.anilbeesetti.nextplayer.feature.player.state.seekToPositionFormated
import dev.anilbeesetti.nextplayer.feature.player.ui.OverlayShowView
import dev.anilbeesetti.nextplayer.feature.player.ui.SubtitleView
import dev.anilbeesetti.nextplayer.feature.player.utils.toMillis
import kotlin.time.Duration.Companion.milliseconds
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR

@OptIn(UnstableApi::class)
@Composable
fun PlayerActivity.MediaPlayerScreen(
    player: MediaController,
    modifier: Modifier = Modifier,
    onSelectSubtitleClick: () -> Unit = {},
) {
    val presentationState = rememberPresentationState(player)
    val metadataState = rememberMetadataState(player)
    val controlsVisibilityState = rememberControlsVisibilityState(
        player = player,
        hideAfter = playerPreferences.controllerAutoHideTimeout.toMillis.milliseconds,
    )
    val doubleTapGestureHandler = rememberDoubleTapGestureHandler(
        player = player,
        doubleTapGesture = playerPreferences.doubleTapGesture,
        seekIncrementMillis = playerPreferences.seekIncrement.toMillis.toLong(),
        shouldFastSeek = { playerPreferences.shouldFastSeek(it) },
    )
    val seekGestureState = rememberSeekGestureState(
        player = player,
        shouldFastSeek = { playerPreferences.shouldFastSeek(it) },
    )
    val pictureInPictureState = rememberPictureInPictureState(
        player = player,
        autoEnter = playerPreferences.autoPip,
    )

    LaunchedEffect(pictureInPictureState.isInPictureInPictureMode) {
        if (pictureInPictureState.isInPictureInPictureMode) {
            controlsVisibilityState.hideControls()
        }
    }

    var videoZoom by remember { mutableStateOf(playerPreferences.playerVideoZoom) }
    var overlayView by remember { mutableStateOf<OverlayView?>(null) }

    Box {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(pictureInPictureState.isInPictureInPictureMode) {
                    if (pictureInPictureState.isInPictureInPictureMode) return@pointerInput

                    detectTapGestures(
                        onTap = {
                            controlsVisibilityState.toggleControlsVisibility()
                        },
                        onDoubleTap = {
                            if (controlsVisibilityState.controlsLocked) return@detectTapGestures
                            doubleTapGestureHandler.handleDoubleTap(offset = it, size = size)
                        },
                    )
                }
                .pointerInput(
                    controlsVisibilityState.controlsLocked,
                    pictureInPictureState.isInPictureInPictureMode,
                ) {
                    if (controlsVisibilityState.controlsLocked) return@pointerInput
                    if (pictureInPictureState.isInPictureInPictureMode) return@pointerInput

                    detectHorizontalDragGestures(
                        onDragStart = seekGestureState::onDragStart,
                        onHorizontalDrag = seekGestureState::onDrag,
                        onDragEnd = seekGestureState::onDragEnd,
                    )
                },
        ) {
            PlayerSurface(
                player = player,
                surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                modifier = Modifier
                    .resizeWithContentScale(
                        contentScale = videoZoom.toContentScale(),
                        sourceSizeDp = presentationState.videoSizeDp,
                    )
                    .onGloballyPositioned {
                        val bounds = it.boundsInWindow()
                        val rect = Rect(
                            bounds.left.toInt(),
                            bounds.top.toInt(),
                            bounds.right.toInt(),
                            bounds.bottom.toInt()
                        )
                        pictureInPictureState.setVideoViewRect(rect)
                    },
            )

            SubtitleView(
                player = player,
                isInPictureInPictureMode = pictureInPictureState.isInPictureInPictureMode,
                playerPreferences = playerPreferences,
            )

            if (presentationState.coverSurface) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 8.dp),
            ) {
                if (controlsVisibilityState.controlsVisible && controlsVisibilityState.controlsLocked) {
                    PlayerButton(onClick = { controlsVisibilityState.unlockControls() }) {
                        Icon(
                            painter = painterResource(coreUiR.drawable.ic_lock),
                            contentDescription = stringResource(coreUiR.string.controls_unlock),
                        )
                    }

                    return
                }

                if (controlsVisibilityState.controlsVisible) {
                    ControlsTopView(
                        title = metadataState.title ?: "",
                        onClickAudioTrackSelector = {
                            controlsVisibilityState.hideControls()
                            overlayView = OverlayView.AUDIO_SELECTOR
                        },
                        onClickSubtitleTrackSelector = {
                            controlsVisibilityState.hideControls()
                            overlayView = OverlayView.SUBTITLE_SELECTOR
                        },
                        onClickPlaybackSpeedSelector = {
                            controlsVisibilityState.hideControls()
                            overlayView = OverlayView.PLAYBACK_SPEED
                        },
                    )
                }

                // MIDDLE
                Spacer(modifier = Modifier.weight(1f))
                if (seekGestureState.isSeeking) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "${seekGestureState.seekAmountFormatted}\n[${seekGestureState.seekToPositionFormated}]",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else if (controlsVisibilityState.controlsVisible) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(32.dp, alignment = Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PreviousButton(player = player)
                        PlayPauseButton(player = player)
                        NextButton(player = player)
                    }
                }


                // BOTTOM
                Spacer(modifier = Modifier.weight(1f))
                if (controlsVisibilityState.controlsVisible) {
                    val context = LocalContext.current
                    ControlsBottomView(
                        player = player,
                        controlsAlignment = when (playerPreferences.controlButtonsPosition) {
                            ControlButtonsPosition.LEFT -> Alignment.Start
                            ControlButtonsPosition.RIGHT -> Alignment.End
                        },
                        videoZoom = videoZoom,
                        isPipSupported = pictureInPictureState.isPipSupported,
                        onVideoZoomOptionSelected = {
                            videoZoom = it
                            changeAndSaveVideoZoom(videoZoom)
                        },
                        onLockControlsClick = { controlsVisibilityState.lockControls() },
                        onPipClick = {
                            if (!pictureInPictureState.hasPipPermission) {
                                Toast.makeText(context, coreUiR.string.enable_pip_from_settings, Toast.LENGTH_SHORT).show()
                                pictureInPictureState.openPictureInPictureSettings()
                            } else {
                                pictureInPictureState.enterPictureInPictureMode()
                            }
                        },
                    )
                }
            }
        }

        OverlayShowView(
            player = player,
            overlayView = overlayView,
            onDismiss = { overlayView = null },
            onSelectSubtitleClick = onSelectSubtitleClick,
        )
    }
}

enum class OverlayView {
    AUDIO_SELECTOR, SUBTITLE_SELECTOR, PLAYBACK_SPEED
}

val Configuration.isPortrait: Boolean
    get() = orientation == Configuration.ORIENTATION_PORTRAIT


@OptIn(UnstableApi::class)
@Composable
fun PlayerActivity.ControlsTopView(
    modifier: Modifier = Modifier,
    title: String,
    onClickAudioTrackSelector: () -> Unit = {},
    onClickSubtitleTrackSelector: () -> Unit = {},
    onClickPlaybackSpeedSelector: () -> Unit = {},
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PlayerButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
            Icon(
                painter = painterResource(coreUiR.drawable.ic_arrow_left),
                contentDescription = null,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlayerButton(onClick = onClickPlaybackSpeedSelector) {
                Icon(
                    painter = painterResource(coreUiR.drawable.ic_speed),
                    contentDescription = null,
                )
            }
            PlayerButton(onClick = onClickAudioTrackSelector) {
                Icon(
                    painter = painterResource(coreUiR.drawable.ic_audio_track),
                    contentDescription = null,
                )
            }
            PlayerButton(onClick = onClickSubtitleTrackSelector) {
                Icon(
                    painter = painterResource(coreUiR.drawable.ic_subtitle_track),
                    contentDescription = null,
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerActivity.ControlsBottomView(
    modifier: Modifier = Modifier,
    player: MediaController,
    controlsAlignment: Alignment.Horizontal,
    videoZoom: VideoZoom,
    isPipSupported: Boolean,
    onVideoZoomOptionSelected: (VideoZoom) -> Unit,
    onLockControlsClick: () -> Unit,
    onPipClick: () -> Unit = {},
) {
    val mediaPresentationState = rememberMediaPresentationState(player)

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var showPendingPosition by remember { mutableStateOf(false) }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.noRippleClickable { showPendingPosition = !showPendingPosition },
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
            RotationButton()
        }
        Slider(
            value = mediaPresentationState.position.toFloat(),
            valueRange = 0f..mediaPresentationState.duration.toFloat(),
            onValueChange = {
                player.setScrubbingModeEnabled(true)
                player.seekTo(it.toLong())
            },
            onValueChangeFinished = {
                player.setScrubbingModeEnabled(false)
            },
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
                    painter = painterResource(coreUiR.drawable.ic_lock_open),
                    contentDescription = null,
                )
            }
            PlayerButton(
                onClick = { onVideoZoomOptionSelected(videoZoom.next()) },
                onLongClick = {
                    videoZoomOptionsDialog(
                        currentVideoZoom = videoZoom,
                        onVideoZoomOptionSelected = onVideoZoomOptionSelected,
                    ).show()
                },
            ) {
                Icon(
                    painter = when (videoZoom) {
                        VideoZoom.BEST_FIT -> painterResource(coreUiR.drawable.ic_fit_screen)
                        VideoZoom.STRETCH -> painterResource(coreUiR.drawable.ic_aspect_ratio)
                        VideoZoom.CROP -> painterResource(coreUiR.drawable.ic_crop_landscape)
                        VideoZoom.HUNDRED_PERCENT -> painterResource(coreUiR.drawable.ic_width_wide)
                    },
                    contentDescription = null,
                )
            }
            if (isPipSupported) {
                PlayerButton(onClick = onPipClick) {
                    Icon(
                        painter = painterResource(coreUiR.drawable.ic_pip),
                        contentDescription = null,
                    )
                }
            }
            PlayerButton(onClick = { }) {
                Icon(
                    painter = painterResource(coreUiR.drawable.ic_headset),
                    contentDescription = null,
                )
            }
            LoopButton(player = player)
        }
    }
}

private fun VideoZoom.toContentScale(): ContentScale = when (this) {
    VideoZoom.BEST_FIT -> ContentScale.Fit
    VideoZoom.STRETCH -> ContentScale.FillBounds
    VideoZoom.CROP -> ContentScale.Crop
    VideoZoom.HUNDRED_PERCENT -> ContentScale.None // TODO: fix this
}