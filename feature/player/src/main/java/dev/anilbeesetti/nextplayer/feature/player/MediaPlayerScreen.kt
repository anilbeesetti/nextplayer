package dev.anilbeesetti.nextplayer.feature.player

import android.content.res.Configuration
import android.graphics.Rect
import android.widget.Toast
import androidx.annotation.IntRange
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import dev.anilbeesetti.nextplayer.core.model.ControlButtonsPosition
import dev.anilbeesetti.nextplayer.core.model.VideoContentScale
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.player.buttons.LoopButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.NextButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.PlayPauseButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.PlayerButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.PreviousButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.RotationButton
import dev.anilbeesetti.nextplayer.feature.player.extensions.drawableRes
import dev.anilbeesetti.nextplayer.feature.player.extensions.next
import dev.anilbeesetti.nextplayer.feature.player.extensions.noRippleClickable
import dev.anilbeesetti.nextplayer.feature.player.extensions.setScrubbingModeEnabled
import dev.anilbeesetti.nextplayer.feature.player.extensions.shouldFastSeek
import dev.anilbeesetti.nextplayer.feature.player.extensions.toContentScale
import dev.anilbeesetti.nextplayer.feature.player.extensions.toMillis
import dev.anilbeesetti.nextplayer.feature.player.state.VerticalGesture
import dev.anilbeesetti.nextplayer.feature.player.state.durationFormatted
import dev.anilbeesetti.nextplayer.feature.player.state.pendingPositionFormatted
import dev.anilbeesetti.nextplayer.feature.player.state.positionFormatted
import dev.anilbeesetti.nextplayer.feature.player.state.rememberBrightnessState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberControlsVisibilityState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberDoubleTapGestureHandler
import dev.anilbeesetti.nextplayer.feature.player.state.rememberMediaPresentationState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberMetadataState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberPictureInPictureState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberSeekGestureState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberVideoZoomState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberVolumeAndBrightnessGestureState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberVolumeState
import dev.anilbeesetti.nextplayer.feature.player.state.seekAmountFormatted
import dev.anilbeesetti.nextplayer.feature.player.state.seekToPositionFormated
import dev.anilbeesetti.nextplayer.feature.player.ui.OverlayShowView
import dev.anilbeesetti.nextplayer.feature.player.ui.PlayerGestures
import dev.anilbeesetti.nextplayer.feature.player.ui.ShutterView
import dev.anilbeesetti.nextplayer.feature.player.ui.SubtitleView
import kotlin.time.Duration.Companion.milliseconds
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR

@OptIn(UnstableApi::class)
@Composable
fun PlayerActivity.MediaPlayerScreen(
    player: MediaController,
    viewModel: PlayerViewModel,
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
    val videoZoomState = rememberVideoZoomState(
        player = player,
        initialContentScale = playerPreferences.playerVideoZoom,
        onEvent = viewModel::onVideoZoomEvent
    )
    val volumeState = rememberVolumeState(
        showVolumePanelIfHeadsetIsOn = playerPreferences.showSystemVolumePanel,
    )
    val brightnessState = rememberBrightnessState()
    val volumeAndBrightnessGestureState = rememberVolumeAndBrightnessGestureState(
        showVolumePanelIfHeadsetIsOn = playerPreferences.showSystemVolumePanel,
    )

    LaunchedEffect(pictureInPictureState.isInPictureInPictureMode) {
        if (pictureInPictureState.isInPictureInPictureMode) {
            controlsVisibilityState.hideControls()
        }
    }

    var overlayView by remember { mutableStateOf<OverlayView?>(null) }

    Box {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            PlayerSurface(
                player = player,
                surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                modifier = Modifier
                    .resizeWithContentScale(
                        contentScale = videoZoomState.videoContentScale.toContentScale(),
                        sourceSizeDp = presentationState.videoSizeDp,
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
                        scaleX = videoZoomState.zoom
                        scaleY = videoZoomState.zoom
                        translationX = videoZoomState.offset.x
                        translationY = videoZoomState.offset.y
                    },
            )

            PlayerGestures(
                controlsVisibilityState = controlsVisibilityState,
                doubleTapGestureHandler = doubleTapGestureHandler,
                pictureInPictureState = pictureInPictureState,
                seekGestureState = seekGestureState,
                videoZoomState = videoZoomState,
                volumeAndBrightnessGestureState = volumeAndBrightnessGestureState,
            )

            SubtitleView(
                player = player,
                isInPictureInPictureMode = pictureInPictureState.isInPictureInPictureMode,
                playerPreferences = playerPreferences,
            )

            if (presentationState.coverSurface) {
                ShutterView()
            }

            PlayerControlsView(
                topView = {
                    if (controlsVisibilityState.controlsVisible && controlsVisibilityState.controlsLocked) {
                        PlayerButton(onClick = { controlsVisibilityState.unlockControls() }) {
                            Icon(
                                painter = painterResource(coreUiR.drawable.ic_lock),
                                contentDescription = stringResource(coreUiR.string.controls_unlock),
                            )
                        }

                        return@PlayerControlsView
                    }

                    AnimatedVisibility(
                        visible = controlsVisibilityState.controlsVisible,
                        enter = slideInVertically { -it },
                        exit = slideOutVertically { -it },
                    ) {
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
                },
                middleView = {
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
                            horizontalArrangement = Arrangement.spacedBy(40.dp, alignment = Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PreviousButton(player = player)
                            PlayPauseButton(player = player)
                            NextButton(player = player)
                        }
                    }
                },
                bottomView = {
                    AnimatedVisibility(
                        visible = controlsVisibilityState.controlsVisible,
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it },
                    ) {
                        val context = LocalContext.current
                        ControlsBottomView(
                            player = player,
                            controlsAlignment = when (playerPreferences.controlButtonsPosition) {
                                ControlButtonsPosition.LEFT -> Alignment.Start
                                ControlButtonsPosition.RIGHT -> Alignment.End
                            },
                            videoContentScale = videoZoomState.videoContentScale,
                            isPipSupported = pictureInPictureState.isPipSupported,
                            onVideoContentScaleSelected = {
                                videoZoomState.onVideoContentScaleChanged(it)
                            },
                            onClickVideoContentScaleSelector = { overlayView = OverlayView.VIDEO_CONTENT_SCALE },
                            onLockControlsClick = { controlsVisibilityState.lockControls() },
                        ) {
                            if (!pictureInPictureState.hasPipPermission) {
                                Toast.makeText(context, coreUiR.string.enable_pip_from_settings, Toast.LENGTH_SHORT).show()
                                pictureInPictureState.openPictureInPictureSettings()
                            } else {
                                pictureInPictureState.enterPictureInPictureMode()
                            }
                        }
                    }
                },
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .displayCutoutPadding()
                    .padding(24.dp)
            ) {
                AnimatedVisibility(
                    modifier = Modifier.align(Alignment.CenterStart),
                    visible = volumeAndBrightnessGestureState.activeGesture == VerticalGesture.VOLUME,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    VerticalProgressView(
                        value = volumeState.volumePercentage,
                        icon = painterResource(coreUiR.drawable.ic_volume),
                    )
                }

                AnimatedVisibility(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    visible = volumeAndBrightnessGestureState.activeGesture == VerticalGesture.BRIGHTNESS,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    VerticalProgressView(
                        value = brightnessState.brightnessPercentage,
                        icon = painterResource(coreUiR.drawable.ic_brightness),
                    )
                }
            }
        }

        OverlayShowView(
            player = player,
            overlayView = overlayView,
            videoContentScale = videoZoomState.videoContentScale,
            onDismiss = { overlayView = null },
            onSelectSubtitleClick = onSelectSubtitleClick,
            onVideoContentScaleChanged = { videoZoomState.onVideoContentScaleChanged(it) },
        )
    }
}

enum class OverlayView {
    AUDIO_SELECTOR, SUBTITLE_SELECTOR, PLAYBACK_SPEED, VIDEO_CONTENT_SCALE
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
fun ControlsBottomView(
    modifier: Modifier = Modifier,
    player: Player,
    controlsAlignment: Alignment.Horizontal,
    videoContentScale: VideoContentScale,
    isPipSupported: Boolean,
    onVideoContentScaleSelected: (VideoContentScale) -> Unit,
    onClickVideoContentScaleSelector: () -> Unit,
    onLockControlsClick: () -> Unit,
    onPipClick: () -> Unit = {},
) {
    val mediaPresentationState = rememberMediaPresentationState(player)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var showPendingPosition by rememberSaveable { mutableStateOf(false) }

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
                onClick = { onVideoContentScaleSelected(videoContentScale.next()) },
                onLongClick = onClickVideoContentScaleSelector,
            ) {
                Icon(
                    painter = painterResource(videoContentScale.drawableRes()),
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

@Composable
fun VerticalProgressView(
    modifier: Modifier = Modifier,
    width: Dp = 32.dp,
    icon: Painter,
    @IntRange(from = 0, to = 100) value: Int,
) {
    Column(
        modifier = modifier
            .heightIn(max = 250.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.background)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier.size(width),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = value.toString(),
                style = MaterialTheme.typography.labelLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground
                ),
                autoSize = TextAutoSize.StepBased(maxFontSize = MaterialTheme.typography.labelLarge.fontSize),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .width(width)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .width(width)
                    .fillMaxHeight(value / 100f)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Box(
            modifier = Modifier.size(width),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Preview
@Composable
fun VerticalProgressPreview() {
    NextPlayerTheme {
        VerticalProgressView(
            value = 50,
            icon = painterResource(coreUiR.drawable.ic_volume)
        )
    }
}

@Composable
fun PlayerControlsView(
    modifier: Modifier = Modifier,
    topView: @Composable () -> Unit,
    middleView: @Composable BoxScope.() -> Unit,
    bottomView: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(horizontal = 8.dp),
        ) {
            topView()
            Spacer(modifier = Modifier.weight(1f))
            bottomView()
        }

        middleView()
    }
}