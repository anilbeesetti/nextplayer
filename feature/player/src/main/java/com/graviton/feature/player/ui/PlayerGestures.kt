package com.graviton.feature.player.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.graviton.core.model.LongPressGesture
import com.graviton.feature.player.extensions.detectCustomHorizontalDragGestures
import com.graviton.feature.player.extensions.detectCustomTransformGestures
import com.graviton.feature.player.extensions.detectCustomVerticalDragGestures
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
    var wasHolding by remember { mutableStateOf(false) }
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
                ) {
                    if (pictureInPictureState.isInPictureInPictureMode) return@pointerInput

                    detectTapGestures(
                        onTap = {
                            // A hold that just ended is not a tap: it must not toggle the controls.
                            if (wasHolding) {
                                wasHolding = false
                                return@detectTapGestures
                            }
                            if (tapGestureState.seekMillis != 0L) return@detectTapGestures
                            controlsVisibilityState.toggleControlsVisibility()
                        },
                        onDoubleTap = {
                            if (controlsVisibilityState.controlsLocked) return@detectTapGestures
                            tapGestureState.handleDoubleTap(offset = it, size = size)
                        },
                        onPress = {
                            // tryAwaitRelease() also returns false on cancel, so the seek and the
                            // speed are completed even when the gesture is stolen by a drag or PiP.
                            tryAwaitRelease()
                            wasHolding = tapGestureState.isLongPressGestureInAction ||
                                longPressSeekState.isActive
                            tapGestureState.handleOnLongPressRelease()
                            longPressSeekState.stop()
                        },
                        onLongPress = { offset ->
                            if (controlsVisibilityState.controlsLocked) return@detectTapGestures
                            when (longPressAction) {
                                LongPressGesture.SEEK -> {
                                    val started = longPressSeekState.start(
                                        x = offset.x,
                                        width = size.width,
                                        incrementMs = seekIncrementMs,
                                    )
                                    if (started) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }

                                LongPressGesture.SPEED_BOOST -> {
                                    tapGestureState.handleLongPress(offset = offset)
                                    if (tapGestureState.isLongPressGestureInAction) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }

                                LongPressGesture.NONE -> Unit
                            }
                        },
                    )
                }
                .pointerInput(
                    controlsVisibilityState.controlsLocked,
                    pictureInPictureState.isInPictureInPictureMode,
                    swipeThresholdPx,
                ) {
                    if (controlsVisibilityState.controlsLocked) return@pointerInput
                    if (pictureInPictureState.isInPictureInPictureMode) return@pointerInput

                    detectCustomHorizontalDragGestures(
                        onDragStart = { offset ->
                            // A hold gesture owns the finger: it must not become a scrub.
                            if (!tapGestureState.isLongPressGestureInAction && !longPressSeekState.isActive) {
                                seekGestureState.onDragStart(offset)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (tapGestureState.isLongPressGestureInAction) {
                                val speedChanged = tapGestureState.handleLongPressDrag(
                                    currentX = change.position.x,
                                    screenWidth = size.width.toFloat(),
                                    swipeThresholdPx = swipeThresholdPx,
                                )
                                if (speedChanged) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                change.consume()
                            } else if (longPressSeekState.isActive) {
                                // The hold owns the horizontal axis while it runs.
                                change.consume()
                            } else {
                                seekGestureState.onDrag(change, dragAmount)
                            }
                        },
                        onDragCancel = {
                            if (!tapGestureState.isLongPressGestureInAction && !longPressSeekState.isActive) {
                                seekGestureState.onDragEnd()
                            }
                        },
                        onDragEnd = {
                            if (!tapGestureState.isLongPressGestureInAction && !longPressSeekState.isActive) {
                                seekGestureState.onDragEnd()
                            }
                        },
                    )
                }
                .pointerInput(
                    controlsVisibilityState.controlsLocked,
                    pictureInPictureState.isInPictureInPictureMode,
                ) {
                    if (controlsVisibilityState.controlsLocked) return@pointerInput
                    if (pictureInPictureState.isInPictureInPictureMode) return@pointerInput

                    detectCustomVerticalDragGestures(
                        onDragStart = { offset ->
                            if (!tapGestureState.isLongPressGestureInAction && !longPressSeekState.isActive) {
                                volumeAndBrightnessGestureState.onDragStart(offset, size)
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (tapGestureState.isLongPressGestureInAction || longPressSeekState.isActive) {
                                change.consume()
                            } else {
                                volumeAndBrightnessGestureState.onDrag(change, dragAmount)
                            }
                        },
                        onDragCancel = {
                            if (!tapGestureState.isLongPressGestureInAction && !longPressSeekState.isActive) {
                                volumeAndBrightnessGestureState.onDragEnd()
                            }
                        },
                        onDragEnd = {
                            if (!tapGestureState.isLongPressGestureInAction && !longPressSeekState.isActive) {
                                volumeAndBrightnessGestureState.onDragEnd()
                            }
                        },
                    )
                }
                .pointerInput(
                    controlsVisibilityState.controlsLocked,
                    pictureInPictureState.isInPictureInPictureMode,
                ) {
                    if (controlsVisibilityState.controlsLocked) return@pointerInput
                    if (pictureInPictureState.isInPictureInPictureMode) return@pointerInput

                    detectCustomTransformGestures(
                        onGesture = { _, panChange, zoomChange, _ ->
                            if (tapGestureState.isLongPressGestureInAction || longPressSeekState.isActive) {
                                return@detectCustomTransformGestures
                            }
                            videoZoomAndContentScaleState.onZoomPanGesture(
                                constraints = this@BoxWithConstraints.constraints,
                                panChange = panChange,
                                zoomChange = zoomChange,
                            )
                        },
                        onGestureEnd = {
                            videoZoomAndContentScaleState.onZoomPanGestureEnd()
                        },
                    )
                },
        )
    }
}
