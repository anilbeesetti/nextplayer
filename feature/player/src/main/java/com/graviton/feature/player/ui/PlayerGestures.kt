package com.graviton.feature.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import com.graviton.core.model.LongPressGesture
import com.graviton.feature.player.extensions.DOUBLE_TAP_TIMEOUT_MS
import com.graviton.feature.player.extensions.detectPlayerGestures
import com.graviton.feature.player.gestures.DragAxis
import com.graviton.feature.player.gestures.PlayerGesture
import com.graviton.feature.player.state.ControlsVisibilityState
import com.graviton.feature.player.state.HoldSpeedGesture
import com.graviton.feature.player.state.LongPressSeekState
import com.graviton.feature.player.state.PictureInPictureState
import com.graviton.feature.player.state.SeekGestureState
import com.graviton.feature.player.state.TapGestureState
import com.graviton.feature.player.state.VideoZoomAndContentScaleState
import com.graviton.feature.player.state.VolumeAndBrightnessGestureState

@Composable
fun PlayerGestures(
    modifier: Modifier = Modifier,
    controlsVisibilityState: ControlsVisibilityState,
    tapGestureState: TapGestureState,
    longPressSeekState: LongPressSeekState,
    longPressAction: LongPressGesture,
    seekIncrementMs: Long,
    pictureInPictureState: PictureInPictureState,
    seekGestureState: SeekGestureState,
    videoZoomAndContentScaleState: VideoZoomAndContentScaleState,
    volumeAndBrightnessGestureState: VolumeAndBrightnessGestureState,
) {
    val haptic = LocalHapticFeedback.current
    val longPressTimeoutMs = LocalViewConfiguration.current.longPressTimeoutMillis
    val swipeThresholdPx = with(LocalDensity.current) { HoldSpeedGesture.SWIPE_THRESHOLD_DP.dp.toPx() }

    // One tick per acceleration step, so the rising seek speed is felt as well as seen.
    LaunchedEffect(longPressSeekState.tier) {
        if (longPressSeekState.isActive && longPressSeekState.tier > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    BoxWithConstraints {
        Box(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(
                    controlsVisibilityState.controlsLocked,
                    pictureInPictureState.isInPictureInPictureMode,
                    longPressAction,
                    seekIncrementMs,
                    swipeThresholdPx,
                ) {
                    if (pictureInPictureState.isInPictureInPictureMode) return@pointerInput

                    val locked = controlsVisibilityState.controlsLocked
                    awaitPointerEventScope {
                        detectPlayerGestures(
                            longPressTimeoutMs = longPressTimeoutMs,
                            doubleTapTimeoutMs = DOUBLE_TAP_TIMEOUT_MS,
                            onGesture = { gesture ->
                                when (gesture) {
                                    // A single tap toggles the controls even while they are locked: the
                                    // lock button is what has to stay reachable.
                                    is PlayerGesture.Tap -> controlsVisibilityState.toggleControlsVisibility()

                                    is PlayerGesture.DoubleTap -> if (!locked) {
                                        tapGestureState.handleDoubleTap(
                                            offset = Offset(gesture.x, gesture.y),
                                            size = size,
                                        )
                                    }

                                    is PlayerGesture.HoldStart -> if (!locked) {
                                        when (longPressAction) {
                                            LongPressGesture.SEEK -> {
                                                val started = longPressSeekState.start(
                                                    x = gesture.x,
                                                    width = size.width,
                                                    incrementMs = seekIncrementMs,
                                                )
                                                if (started) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            }

                                            LongPressGesture.SPEED_BOOST -> {
                                                tapGestureState.handleLongPress(
                                                    offset = Offset(gesture.x, gesture.y),
                                                )
                                                if (tapGestureState.isLongPressGestureInAction) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            }

                                            LongPressGesture.NONE -> Unit
                                        }
                                    }

                                    is PlayerGesture.HoldMove -> if (
                                        longPressAction == LongPressGesture.SPEED_BOOST &&
                                        tapGestureState.isLongPressGestureInAction
                                    ) {
                                        val speedChanged = tapGestureState.handleLongPressDrag(
                                            currentX = gesture.x,
                                            screenWidth = size.width.toFloat(),
                                            swipeThresholdPx = swipeThresholdPx,
                                        )
                                        if (speedChanged) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }

                                    is PlayerGesture.HoldEnd -> {
                                        tapGestureState.handleOnLongPressRelease()
                                        longPressSeekState.stop()
                                    }

                                    is PlayerGesture.DragStart -> if (!locked) {
                                        when (gesture.axis) {
                                            DragAxis.HORIZONTAL -> seekGestureState.onDragStart(
                                                Offset(gesture.x, gesture.y),
                                            )

                                            DragAxis.VERTICAL -> volumeAndBrightnessGestureState.onDragStart(
                                                offset = Offset(gesture.x, gesture.y),
                                                size = size,
                                            )
                                        }
                                    }

                                    is PlayerGesture.DragMove -> if (!locked) {
                                        when (gesture.axis) {
                                            DragAxis.HORIZONTAL -> seekGestureState.onDrag(
                                                positionX = gesture.x,
                                                dragAmount = gesture.dx,
                                            )

                                            DragAxis.VERTICAL -> volumeAndBrightnessGestureState.onDrag(
                                                positionY = gesture.y,
                                            )
                                        }
                                    }

                                    is PlayerGesture.DragEnd -> if (!locked) {
                                        when (gesture.axis) {
                                            DragAxis.HORIZONTAL -> seekGestureState.onDragEnd()
                                            DragAxis.VERTICAL -> volumeAndBrightnessGestureState.onDragEnd()
                                        }
                                    }
                                }
                            },
                            onPinch = { _, pan, zoom ->
                                if (!locked) {
                                    videoZoomAndContentScaleState.onZoomPanGesture(
                                        constraints = this@BoxWithConstraints.constraints,
                                        panChange = pan,
                                        zoomChange = zoom,
                                    )
                                }
                            },
                            onPinchEnd = {
                                videoZoomAndContentScaleState.onZoomPanGestureEnd()
                            },
                        )
                    }
                },
        )
    }
}
