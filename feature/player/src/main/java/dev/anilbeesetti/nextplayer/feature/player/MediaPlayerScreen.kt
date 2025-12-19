package dev.anilbeesetti.nextplayer.feature.player

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import dev.anilbeesetti.nextplayer.core.model.ControlButtonsPosition
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.feature.player.buttons.NextButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.PlayPauseButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.PlayerButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.PreviousButton
import dev.anilbeesetti.nextplayer.feature.player.extensions.next
import dev.anilbeesetti.nextplayer.feature.player.extensions.shouldFastSeek
import dev.anilbeesetti.nextplayer.feature.player.extensions.toMillis
import dev.anilbeesetti.nextplayer.feature.player.state.VerticalGesture
import dev.anilbeesetti.nextplayer.feature.player.state.rememberBrightnessState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberControlsVisibilityState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberDoubleTapGestureHandler
import dev.anilbeesetti.nextplayer.feature.player.state.rememberMetadataState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberPictureInPictureState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberRotationState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberSeekGestureState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberVideoZoomAndContentScaleState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberVolumeAndBrightnessGestureState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberVolumeState
import dev.anilbeesetti.nextplayer.feature.player.state.seekAmountFormatted
import dev.anilbeesetti.nextplayer.feature.player.state.seekToPositionFormated
import dev.anilbeesetti.nextplayer.feature.player.ui.OverlayShowView
import dev.anilbeesetti.nextplayer.feature.player.ui.OverlayView
import dev.anilbeesetti.nextplayer.feature.player.ui.SubtitleConfiguration
import dev.anilbeesetti.nextplayer.feature.player.ui.VerticalProgressView
import dev.anilbeesetti.nextplayer.feature.player.ui.controls.ControlsBottomView
import dev.anilbeesetti.nextplayer.feature.player.ui.controls.ControlsTopView
import kotlin.time.Duration.Companion.milliseconds
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR

@OptIn(UnstableApi::class)
@Composable
fun MediaPlayerScreen(
    player: MediaController,
    viewModel: PlayerViewModel,
    playerPreferences: PlayerPreferences,
    modifier: Modifier = Modifier,
    onSelectSubtitleClick: () -> Unit,
    onBackClick: () -> Unit,
    onPlayInBackgroundClick: () -> Unit
) {
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
    val videoZoomAndContentScaleState = rememberVideoZoomAndContentScaleState(
        player = player,
        initialContentScale = playerPreferences.playerVideoZoom,
        onEvent = viewModel::onVideoZoomEvent,
    )
    val volumeState = rememberVolumeState(
        showVolumePanelIfHeadsetIsOn = playerPreferences.showSystemVolumePanel,
    )
    val brightnessState = rememberBrightnessState()
    val volumeAndBrightnessGestureState = rememberVolumeAndBrightnessGestureState(
        showVolumePanelIfHeadsetIsOn = playerPreferences.showSystemVolumePanel,
    )
    val rotationState = rememberRotationState(
        player = player,
        screenOrientation = playerPreferences.playerScreenOrientation,
    )

    LaunchedEffect(pictureInPictureState.isInPictureInPictureMode) {
        if (pictureInPictureState.isInPictureInPictureMode) {
            controlsVisibilityState.hideControls()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (playerPreferences.rememberPlayerBrightness) {
            brightnessState.setBrightness(playerPreferences.playerBrightness)
        }
    }

    LaunchedEffect(brightnessState.currentBrightness) {
        if (playerPreferences.rememberPlayerBrightness) {
            viewModel.updatePlayerBrightness(brightnessState.currentBrightness)
        }
    }

    var overlayView by remember { mutableStateOf<OverlayView?>(null) }

    Box {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            PlayerContentFrame(
                player = player,
                pictureInPictureState = pictureInPictureState,
                controlsVisibilityState = controlsVisibilityState,
                doubleTapGestureHandler = doubleTapGestureHandler,
                seekGestureState = seekGestureState,
                videoZoomAndContentScaleState = videoZoomAndContentScaleState,
                volumeAndBrightnessGestureState = volumeAndBrightnessGestureState,
                subtitleConfiguration = SubtitleConfiguration(
                    useSystemCaptionStyle = playerPreferences.useSystemCaptionStyle,
                    showBackground = playerPreferences.subtitleBackground,
                    font = playerPreferences.subtitleFont,
                    textSize = playerPreferences.subtitleTextSize,
                    textBold = playerPreferences.subtitleTextBold,
                    applyEmbeddedStyles = playerPreferences.applyEmbeddedStyles,
                ),
            )

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
                            onAudioClick = {
                                controlsVisibilityState.hideControls()
                                overlayView = OverlayView.AUDIO_SELECTOR
                            },
                            onSubtitleClick = {
                                controlsVisibilityState.hideControls()
                                overlayView = OverlayView.SUBTITLE_SELECTOR
                            },
                            onPlaybackSpeedClick = {
                                controlsVisibilityState.hideControls()
                                overlayView = OverlayView.PLAYBACK_SPEED
                            },
                            onBackClick = onBackClick,
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
                    } else if (videoZoomAndContentScaleState.isZooming) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "${(videoZoomAndContentScaleState.zoom * 100).toInt()}%",
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
                            videoContentScale = videoZoomAndContentScaleState.videoContentScale,
                            isPipSupported = pictureInPictureState.isPipSupported,
                            onVideoContentScaleClick = {
                                videoZoomAndContentScaleState.onVideoContentScaleChanged(videoZoomAndContentScaleState.videoContentScale.next())
                            },
                            onVideoContentScaleLongClick = { overlayView = OverlayView.VIDEO_CONTENT_SCALE },
                            onLockControlsClick = { controlsVisibilityState.lockControls() },
                            onRotateClick = { rotationState.rotate() },
                            onPictureInPictureClick = {
                                if (!pictureInPictureState.hasPipPermission) {
                                    Toast.makeText(context, coreUiR.string.enable_pip_from_settings, Toast.LENGTH_SHORT).show()
                                    pictureInPictureState.openPictureInPictureSettings()
                                } else {
                                    pictureInPictureState.enterPictureInPictureMode()
                                }
                            },
                            onPlayInBackgroundClick = onPlayInBackgroundClick
                        )
                    }
                },
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .displayCutoutPadding()
                    .padding(24.dp),
            ) {
                AnimatedVisibility(
                    modifier = Modifier.align(Alignment.CenterStart),
                    visible = volumeAndBrightnessGestureState.activeGesture == VerticalGesture.VOLUME,
                    enter = fadeIn(),
                    exit = fadeOut(),
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
                    exit = fadeOut(),
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
            videoContentScale = videoZoomAndContentScaleState.videoContentScale,
            onDismiss = { overlayView = null },
            onSelectSubtitleClick = onSelectSubtitleClick,
            onVideoContentScaleChanged = { videoZoomAndContentScaleState.onVideoContentScaleChanged(it) },
        )
    }

    BackHandler {
        onBackClick()
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