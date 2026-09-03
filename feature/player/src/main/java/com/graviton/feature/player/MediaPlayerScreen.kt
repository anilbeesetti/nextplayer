package com.graviton.feature.player

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.graviton.core.common.extensions.isTelevision
import com.graviton.core.model.ControlButtonsPosition
import com.graviton.core.model.PlayerPreferences
import com.graviton.core.ui.R as coreUiR
import com.graviton.core.ui.components.requestFocusUntilLanded
import com.graviton.core.ui.components.thenIf
import com.graviton.core.ui.extensions.copy
import com.graviton.feature.player.buttons.NextButton
import com.graviton.feature.player.buttons.PlayPauseButton
import com.graviton.feature.player.buttons.PlayerButton
import com.graviton.feature.player.buttons.PreviousButton
import com.graviton.feature.player.extensions.formatted
import com.graviton.feature.player.extensions.nameRes
import com.graviton.feature.player.state.ControlsVisibilityState
import com.graviton.feature.player.state.VerticalGesture
import com.graviton.feature.player.state.rememberBrightnessState
import com.graviton.feature.player.state.rememberControlsVisibilityState
import com.graviton.feature.player.state.rememberCutSegmentState
import com.graviton.feature.player.state.rememberErrorState
import com.graviton.feature.player.state.rememberMediaPresentationState
import com.graviton.feature.player.state.rememberMetadataState
import com.graviton.feature.player.state.rememberPictureInPictureState
import com.graviton.feature.player.state.rememberPlaybackDiagnosticsState
import com.graviton.feature.player.state.rememberRotationState
import com.graviton.feature.player.state.rememberSeekGestureState
import com.graviton.feature.player.state.rememberSubtitleOptionsState
import com.graviton.feature.player.state.rememberTapGestureState
import com.graviton.feature.player.state.rememberTracksState
import com.graviton.feature.player.state.rememberVideoZoomAndContentScaleState
import com.graviton.feature.player.state.rememberVolumeAndBrightnessGestureState
import com.graviton.feature.player.state.rememberVolumeState
import com.graviton.feature.player.state.seekAmountFormatted
import com.graviton.feature.player.state.seekToPositionFormated
import com.graviton.feature.player.ui.DoubleTapIndicator
import com.graviton.feature.player.ui.OverlayShowView
import com.graviton.feature.player.ui.OverlayView
import com.graviton.feature.player.ui.SpeedOverlayView
import com.graviton.feature.player.ui.SubtitleConfiguration
import com.graviton.feature.player.ui.VerticalProgressView
import com.graviton.feature.player.ui.controls.ControlsBottomView
import com.graviton.feature.player.ui.controls.ControlsTopView
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

val LocalControlsVisibilityState = compositionLocalOf<ControlsVisibilityState?> { null }

@OptIn(UnstableApi::class)
@Composable
fun MediaPlayerScreen(
    player: Player?,
    viewModel: PlayerViewModel,
    uiState: PlayerUiState,
    playerPreferences: PlayerPreferences,
    modifier: Modifier = Modifier,
    onSelectSubtitleClick: () -> Unit,
    onBackClick: () -> Unit,
    onPlayInBackgroundClick: () -> Unit,
    onNetworkStreamClick: () -> Unit,
    onShareClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val volumeState = rememberVolumeState(
        player = player,
        showVolumePanelIfHeadsetIsOn = playerPreferences.showSystemVolumePanel,
    )
    player ?: return
    val metadataState = rememberMetadataState(player)
    val mediaPresentationState = rememberMediaPresentationState(player)
    val controlsVisibilityState = rememberControlsVisibilityState(
        player = player,
        hideAfter = playerPreferences.controllerAutoHideTimeout.seconds,
    )
    val tapGestureState = rememberTapGestureState(
        player = player,
        doubleTapGesture = playerPreferences.doubleTapGesture,
        seekIncrementMillis = playerPreferences.seekIncrement.seconds.inWholeMilliseconds,
        useLongPressGesture = playerPreferences.useLongPressControls,
        longPressSpeed = playerPreferences.longPressControlsSpeed,
    )
    val seekGestureState = rememberSeekGestureState(
        player = player,
        sensitivity = playerPreferences.seekSensitivity,
        enableSeekGesture = playerPreferences.useSeekControls,
    )
    val pictureInPictureState = rememberPictureInPictureState(
        player = player,
        autoEnter = playerPreferences.autoPip,
    )
    val videoZoomAndContentScaleState = rememberVideoZoomAndContentScaleState(
        player = player,
        initialContentScale = playerPreferences.playerVideoZoom,
        enableZoomGesture = playerPreferences.useZoomControls,
        enablePanGesture = playerPreferences.enablePanGesture,
        onEvent = viewModel::onVideoZoomEvent,
    )
    val brightnessState = rememberBrightnessState()
    val volumeAndBrightnessGestureState = rememberVolumeAndBrightnessGestureState(
        volumeState = volumeState,
        brightnessState = brightnessState,
        enableVolumeGesture = playerPreferences.enableVolumeSwipeGesture,
        enableBrightnessGesture = playerPreferences.enableBrightnessSwipeGesture,
        volumeGestureSensitivity = playerPreferences.volumeGestureSensitivity,
        brightnessGestureSensitivity = playerPreferences.brightnessGestureSensitivity,
    )
    val rotationState = rememberRotationState(
        player = player,
        screenOrientation = playerPreferences.playerScreenOrientation,
    )
    val errorState = rememberErrorState(player = player)
    val subtitleTracksState = rememberTracksState(player, C.TRACK_TYPE_TEXT)
    val subtitleOptionsState = rememberSubtitleOptionsState(player, viewModel::onSubtitleOptionEvent)
    val cutSegmentState = rememberCutSegmentState(player)

    LaunchedEffect(pictureInPictureState.isInPictureInPictureMode) {
        if (pictureInPictureState.isInPictureInPictureMode) {
            controlsVisibilityState.hideControls()
        }
    }

    LaunchedEffect(tapGestureState.isLongPressGestureInAction) {
        if (tapGestureState.isLongPressGestureInAction) {
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

    val currentMediaId = metadataState.mediaId
    // Chapter sidecars and container info are read once per item, when the item changes.
    LaunchedEffect(currentMediaId) {
        currentMediaId?.let(viewModel::loadMediaDetails)
    }

    // The gesture onboarding is offered once, and only when gestures actually exist to explain.
    LaunchedEffect(uiState.isTutorialShown) {
        if (!uiState.isTutorialShown && overlayView == null) {
            overlayView = OverlayView.TUTORIAL
        }
    }

    val diagnostics = rememberPlaybackDiagnosticsState(
        player = player,
        enabled = overlayView == OverlayView.VIDEO_INFORMATION,
    )
    val bookmarks = currentMediaId?.let { uiState.bookmarks[it] }.orEmpty()

    // Opening a sheet always hides the controls first, so the sheet is never stacked on top of a
    // control bar that is about to auto-hide underneath it.
    val openOverlay: (OverlayView) -> Unit = { target ->
        controlsVisibilityState.hideControls()
        overlayView = target
    }

    val context = LocalContext.current
    val isTv = remember { context.isTelevision }
    val rootFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val seekBarFocusRequester = remember { FocusRequester() }
    val unlockFocusRequester = remember { FocusRequester() }
    var isPlayPauseFocused by remember { mutableStateOf(false) }
    var isUnlockFocused by remember { mutableStateOf(false) }
    val seekIncrementMs = playerPreferences.seekIncrement.seconds.inWholeMilliseconds

    if (isTv) {
        LaunchedEffect(controlsVisibilityState.controlsVisible, controlsVisibilityState.controlsLocked, overlayView) {
            if (overlayView != null) return@LaunchedEffect
            if (!controlsVisibilityState.controlsVisible) {
                runCatching { rootFocusRequester.requestFocus() }
                return@LaunchedEffect
            }
            val locked = controlsVisibilityState.controlsLocked
            val target = if (locked) unlockFocusRequester else playPauseFocusRequester
            target.requestFocusUntilLanded(attempts = 20) { if (locked) isUnlockFocused else isPlayPauseFocused }
        }
    }

    // D-pad seeking (controls hidden): accumulate the skipped amount and briefly show it.
    var dpadSeekOffsetMs by remember { mutableLongStateOf(0L) }
    var dpadSeekTargetMs by remember { mutableLongStateOf(0L) }
    var dpadSeekActive by remember { mutableStateOf(false) }
    var dpadSeekTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(dpadSeekTick) {
        if (!dpadSeekActive) return@LaunchedEffect
        delay(1.seconds)
        dpadSeekActive = false
    }

    val showDpadSeekFeedback: (Long) -> Unit = { deltaMs ->
        if (!dpadSeekActive) dpadSeekOffsetMs = 0L
        dpadSeekOffsetMs += deltaMs
        dpadSeekTargetMs = player.currentPosition
        dpadSeekActive = true
        dpadSeekTick++
    }

    CompositionLocalProvider(LocalControlsVisibilityState provides controlsVisibilityState) {
        Box {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .then(
                        if (isTv) {
                            Modifier
                                .focusRequester(rootFocusRequester)
                                .focusable()
                                .onPreviewKeyEvent { keyEvent ->
                                    if (overlayView != null) {
                                        false
                                    } else {
                                        handlePlayerKeyEvent(
                                            keyEvent = keyEvent,
                                            player = player,
                                            controls = controlsVisibilityState,
                                            seekIncrementMs = seekIncrementMs,
                                            isPlayPauseFocused = isPlayPauseFocused,
                                            onDpadSeek = showDpadSeekFeedback,
                                        )
                                    }
                                }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                PlayerContentFrame(
                    player = player,
                    pictureInPictureState = pictureInPictureState,
                    controlsVisibilityState = controlsVisibilityState,
                    tapGestureState = tapGestureState,
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

                AnimatedVisibility(
                    visible = controlsVisibilityState.controlsVisible && !controlsVisibilityState.controlsLocked,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                    )
                }

                if (mediaPresentationState.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(72.dp),
                    )
                }

                DoubleTapIndicator(tapGestureState = tapGestureState)

                DpadSeekIndicator(
                    visible = dpadSeekActive && dpadSeekOffsetMs != 0L,
                    offsetMs = dpadSeekOffsetMs,
                    positionMs = dpadSeekTargetMs,
                )

                if (controlsVisibilityState.controlsVisible && controlsVisibilityState.controlsLocked) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .padding(top = 24.dp),
                    ) {
                        PlayerButton(
                            modifier = Modifier.thenIf(isTv) {
                                focusRequester(unlockFocusRequester)
                                    .onFocusChanged { isUnlockFocused = it.hasFocus }
                            },
                            containerColor = Color.Black.copy(0.5f),
                            onClick = { controlsVisibilityState.unlockControls() }
                        ) {
                            Icon(
                                painter = painterResource(coreUiR.drawable.ic_lock),
                                contentDescription = stringResource(coreUiR.string.controls_unlock),
                            )
                        }
                    }
                } else {
                    PlayerControlsView(
                        topView = {
                            AnimatedVisibility(
                                visible = controlsVisibilityState.controlsVisible,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                ControlsTopView(
                                    title = metadataState.title ?: "",
                                    currentDecoderMode = playerPreferences.decoderMode,
                                    onAudioClick = { openOverlay(OverlayView.AUDIO_SELECTOR) },
                                    onSubtitleClick = { openOverlay(OverlayView.SUBTITLE_SELECTOR) },
                                    onPlaybackSpeedClick = { openOverlay(OverlayView.PLAYBACK_SPEED) },
                                    onDecoderClick = { openOverlay(OverlayView.DECODER_SELECTOR) },
                                    onMoreClick = { openOverlay(OverlayView.MORE_OPTIONS) },
                                    onBackClick = onBackClick,
                                )
                            }
                        },
                        middleView = {
                            when {
                                seekGestureState.seekAmount != null -> InfoView(info = "${seekGestureState.seekAmountFormatted}\n[${seekGestureState.seekToPositionFormated}]")
                                videoZoomAndContentScaleState.isZooming -> InfoView(info = "${(videoZoomAndContentScaleState.zoom * 100).toInt()}%")
                                videoZoomAndContentScaleState.showContentScaleIndicator -> InfoView(info = stringResource(videoZoomAndContentScaleState.videoContentScale.nameRes()))
                                controlsVisibilityState.controlsVisible -> ControlsMiddleView(
                                    player = player,
                                    playPauseModifier = Modifier.thenIf(isTv) {
                                        focusRequester(playPauseFocusRequester)
                                            .onFocusChanged { isPlayPauseFocused = it.hasFocus }
                                    },
                                )
                                else -> Unit
                            }
                        },
                        bottomView = {
                            AnimatedVisibility(
                                visible = controlsVisibilityState.controlsVisible && !controlsVisibilityState.controlsLocked,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                val context = LocalContext.current
                                ControlsBottomView(
                                    player = player,
                                    mediaPresentationState = mediaPresentationState,
                                    controlsAlignment = when (playerPreferences.controlButtonsPosition) {
                                        ControlButtonsPosition.LEFT -> Alignment.Start
                                        ControlButtonsPosition.RIGHT -> Alignment.End
                                    },
                                    videoContentScale = videoZoomAndContentScaleState.videoContentScale,
                                    isPipSupported = pictureInPictureState.isPipSupported,
                                    seekBarModifier = Modifier.thenIf(isTv) {
                                        focusRequester(seekBarFocusRequester)
                                            .focusProperties { up = playPauseFocusRequester }
                                    },
                                    onSeek = seekGestureState::onSeek,
                                    onSeekEnd = seekGestureState::onSeekEnd,
                                    onRotateClick = rotationState::rotate,
                                    onPlayInBackgroundClick = onPlayInBackgroundClick,
                                    onLockControlsClick = {
                                        controlsVisibilityState.showControls()
                                        controlsVisibilityState.lockControls()
                                    },
                                    onVideoContentScaleClick = {
                                        controlsVisibilityState.showControls()
                                        videoZoomAndContentScaleState.switchToNextVideoContentScale()
                                    },
                                    onVideoContentScaleLongClick = { openOverlay(OverlayView.VIDEO_CONTENT_SCALE) },
                                    onPictureInPictureClick = {
                                        if (!pictureInPictureState.hasPipPermission) {
                                            Toast.makeText(context, coreUiR.string.enable_pip_from_settings, Toast.LENGTH_SHORT).show()
                                            pictureInPictureState.openPictureInPictureSettings()
                                        } else {
                                            pictureInPictureState.enterPictureInPictureMode()
                                        }
                                    },
                                )
                            }
                        },
                    )
                }

                val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .displayCutoutPadding()
                        .padding(systemBarsPadding.copy(top = 0.dp, bottom = 0.dp))
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
                            maxValue = volumeState.maxVolumePercentage,
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
                playerPreferences = playerPreferences,
                videoZoomAndContentScaleState = videoZoomAndContentScaleState,
                subtitleTracksState = subtitleTracksState,
                subtitleOptionsState = subtitleOptionsState,
                cutSegmentState = cutSegmentState,
                mediaPresentationState = mediaPresentationState,
                diagnostics = diagnostics,
                bookmarks = bookmarks,
                chapters = uiState.chapters,
                mediaInfo = uiState.mediaInfo,
                isLoadingDetails = uiState.isLoadingDetails,
                isPipSupported = pictureInPictureState.isPipSupported,
                aspectRatioLabel = stringResource(videoZoomAndContentScaleState.videoContentScale.nameRes()),
                longPressSpeedLabel = "%.1f\u00D7".format(playerPreferences.longPressControlsSpeed),
                bookmarkCountLabel = bookmarks.size.takeIf { it > 0 }?.toString().orEmpty(),
                chapterCountLabel = uiState.chapters.size.takeIf { it > 0 }?.toString().orEmpty(),
                cutSegmentLabel = if (cutSegmentState.hasSegment) {
                    stringResource(coreUiR.string.on)
                } else {
                    ""
                },
                onDismiss = { overlayView = null },
                onOverlayViewChange = { overlayView = it },
                onSelectSubtitleClick = onSelectSubtitleClick,
                onSubtitleOptionEvent = viewModel::onSubtitleOptionEvent,
                onVideoContentScaleChanged = videoZoomAndContentScaleState::onVideoContentScaleChanged,
                onDecoderModeSelected = viewModel::updateDecoderMode,
                onScreenOrientationChanged = viewModel::updateScreenOrientation,
                onPanGestureChanged = viewModel::updatePanGesture,
                onAutoPipChanged = viewModel::updateAutoPip,
                onControllerAutoHideTimeoutChanged = viewModel::updateControllerAutoHideTimeout,
                onGesturesClick = {
                    overlayView = null
                    onSettingsClick()
                },
                onNetworkStreamClick = {
                    overlayView = null
                    onNetworkStreamClick()
                },
                onShareClick = {
                    overlayView = null
                    onShareClick()
                },
                onSettingsClick = {
                    overlayView = null
                    onSettingsClick()
                },
                onAddBookmark = { label ->
                    currentMediaId?.let { mediaId ->
                        viewModel.addBookmark(mediaId, player.currentPosition, label)
                    }
                },
                onDeleteBookmark = { bookmark ->
                    currentMediaId?.let { mediaId -> viewModel.deleteBookmark(mediaId, bookmark) }
                },
                onTutorialDontShowAgain = {
                    viewModel.setTutorialShown(true)
                    overlayView = null
                },
            )

            SpeedOverlayView(
                speed = tapGestureState.activeLongPressSpeed,
                isLongPressActive = tapGestureState.isLongPressGestureInAction,
                controlsVisible = controlsVisibilityState.controlsVisible,
            )
        }

    }

    errorState.error?.let { error ->
        AlertDialog(
            onDismissRequest = { },
            title = {
                Text(text = stringResource(coreUiR.string.error_playing_video))
            },
            text = {
                Text(text = error.message ?: stringResource(coreUiR.string.unknown_error))
            },
            confirmButton = {
                if (player.hasNextMediaItem()) {
                    TextButton(
                        onClick = {
                            errorState.dismiss()
                            player.seekToNext()
                            player.play()
                        },
                    ) {
                        Text(text = stringResource(coreUiR.string.play_next_video))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        errorState.dismiss()
                        onBackClick()
                    },
                ) {
                    Text(text = stringResource(coreUiR.string.exit))
                }
            },
        )
    }

    BackHandler {
        when {
            overlayView != null -> overlayView = null
            isTv && controlsVisibilityState.controlsVisible -> controlsVisibilityState.hideControls()
            else -> onBackClick()
        }
    }
}

@Composable
fun InfoView(
    modifier: Modifier = Modifier,
    info: String,
    textStyle: TextStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = info,
            style = textStyle,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Shows the cumulative amount skipped by repeated D-pad left/right seeks while the controls are
 * hidden, along with the resulting position. Fades out shortly after the last seek.
 */
@Composable
fun BoxScope.DpadSeekIndicator(
    visible: Boolean,
    offsetMs: Long,
    positionMs: Long,
) {
    AnimatedVisibility(
        modifier = Modifier.align(Alignment.Center),
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.6f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painter = painterResource(coreUiR.drawable.ic_fast),
                    contentDescription = if (offsetMs >= 0) stringResource(coreUiR.string.forward_seek_description) else stringResource(coreUiR.string.rewind_seek_description),
                    tint = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .rotate(if (offsetMs < 0) 180f else 0f),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${if (offsetMs >= 0) "+" else "-"}${abs(offsetMs).milliseconds.inWholeSeconds}s",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = positionMs.milliseconds.formatted(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
fun ControlsMiddleView(
    modifier: Modifier = Modifier,
    player: Player,
    playPauseModifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(40.dp, alignment = Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PreviousButton(player = player)
        PlayPauseButton(player = player, modifier = playPauseModifier)
        NextButton(player = player)
    }
}

@OptIn(UnstableApi::class)
private fun handlePlayerKeyEvent(
    keyEvent: KeyEvent,
    player: Player,
    controls: ControlsVisibilityState,
    seekIncrementMs: Long,
    isPlayPauseFocused: Boolean,
    onDpadSeek: (deltaMs: Long) -> Unit,
): Boolean {
    if (keyEvent.key == Key.Back && !controls.controlsLocked) {
        if (!controls.controlsVisible) return false // controls already hidden: let BACK exit
        if (keyEvent.type == KeyEventType.KeyUp) controls.hideControls()
        return true
    }
    if (keyEvent.type != KeyEventType.KeyDown) return false
    if (controls.controlsLocked) {
        return when (keyEvent.key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                if (controls.controlsVisible) controls.unlockControls() else controls.showControls()
                true
            }
            else -> {
                controls.showControls()
                false
            }
        }
    }

    fun seekBy(deltaMs: Long) {
        val duration = player.duration
        val target = (player.currentPosition + deltaMs).coerceAtLeast(0)
        player.seekTo(if (duration > 0) target.coerceAtMost(duration) else target)
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    return when (keyEvent.key) {
        Key.MediaPlayPause, Key.Spacebar -> { togglePlayPause(); controls.showControls(); true }
        Key.MediaPlay -> { player.play(); controls.showControls(); true }
        Key.MediaPause -> { player.pause(); controls.showControls(); true }
        Key.MediaFastForward -> { seekBy(seekIncrementMs); controls.showControls(); true }
        Key.MediaRewind -> { seekBy(-seekIncrementMs); controls.showControls(); true }
        Key.MediaNext -> { player.seekToNext(); controls.showControls(); true }
        Key.MediaPrevious -> { player.seekToPrevious(); controls.showControls(); true }
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
            when {
                !controls.controlsVisible -> {
                    controls.showControls()
                    true
                }
                isPlayPauseFocused -> {
                    togglePlayPause()
                    controls.showControls()
                    true
                }
                else -> false
            }
        }
        Key.DirectionLeft -> {
            if (!controls.controlsVisible) {
                seekBy(-seekIncrementMs)
                onDpadSeek(-seekIncrementMs)
                true
            } else {
                controls.showControls()
                false
            }
        }
        Key.DirectionRight -> {
            if (!controls.controlsVisible) {
                seekBy(seekIncrementMs)
                onDpadSeek(seekIncrementMs)
                true
            } else {
                controls.showControls()
                false
            }
        }
        Key.DirectionUp, Key.DirectionDown -> {
            if (!controls.controlsVisible) {
                controls.showControls()
                true
            } else {
                controls.showControls()
                false
            }
        }
        else -> false
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
        Column {
            topView()
            Spacer(modifier = Modifier.weight(1f))
            bottomView()
        }

        middleView()
    }
}
