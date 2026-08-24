package com.graviton.feature.player.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.graviton.feature.player.extensions.detectCustomHorizontalDragGestures
import com.graviton.feature.player.extensions.detectCustomTransformGestures
import com.graviton.feature.player.extensions.detectCustomVerticalDragGestures
import com.graviton.feature.player.state.ControlsVisibilityState
import com.graviton.feature.player.state.HoldSpeedGesture
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
    pictureInPictureState: PictureInPictureState,
    seekGestureState: SeekGestureState,
    videoZoomAndContentScaleState: VideoZoomAndContentScaleState,
    volumeAndBrightnessGestureState: VolumeAndBrightnessGestureState,
) {
    val haptic = LocalHapticFeedback.current
    val swipeThresholdPx = with(LocalDensity.current) { HoldSpeedGesture.SWIPE_THRESHOLD_DP.dp.toPx() }

    BoxWithConstraints {
        Box(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(pictureInPictureState.isInPictureInPictureMode) {
                    if (pictureInPictureState.isInPictureInPictureMode) return@pointerInput

                    detectTapGestures(
                        onTap = {
                            if (tapGestureState.seekMillis != 0L) return@detectTapGestures
                            controlsVisibilityState.toggleControlsVisibility()
                        },
                        onDoubleTap = {
                            if (controlsVisibilityState.controlsLocked) return@detectTapGestures
                            tapGestureState.handleDoubleTap(offset = it, size = size)
                        },
                        onPress = {
                            tryAwaitRelease()
                            tapGestureState.handleOnLongPressRelease()
                        },
                        onLongPress = {
                            if (controlsVisibilityState.controlsLocked) return@detectTapGestures
                            tapGestureState.handleLongPress(offset = it)
                            if (tapGestureState.isLongPressGestureInAction) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                            if (!tapGestureState.isLongPressGestureInAction) {
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
                            } else {
                                seekGestureState.onDrag(change, dragAmount)
                            }
                        },
                        onDragCancel = {
                            if (!tapGestureState.isLongPressGestureInAction) {
                                seekGestureState.onDragEnd()
                            }
                        },
                        onDragEnd = {
                            if (!tapGestureState.isLongPressGestureInAction) {
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
                            if (!tapGestureState.isLongPressGestureInAction) {
                                volumeAndBrightnessGestureState.onDragStart(offset, size)
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (tapGestureState.isLongPressGestureInAction) {
                                change.consume()
                            } else {
                                volumeAndBrightnessGestureState.onDrag(change, dragAmount)
                            }
                        },
                        onDragCancel = {
                            if (!tapGestureState.isLongPressGestureInAction) {
                                volumeAndBrightnessGestureState.onDragEnd()
                            }
                        },
                        onDragEnd = {
                            if (!tapGestureState.isLongPressGestureInAction) {
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
                            if (tapGestureState.isLongPressGestureInAction) return@detectCustomTransformGestures
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
