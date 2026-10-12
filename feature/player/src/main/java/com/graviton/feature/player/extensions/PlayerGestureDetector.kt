package com.graviton.feature.player.extensions

import android.os.SystemClock
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChanged
import com.graviton.feature.player.gestures.PlayerGesture
import com.graviton.feature.player.gestures.PlayerGestureRecognizer
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The one pointer loop for the video surface. Call it from `awaitPointerEventScope { }` inside a
 * `pointerInput` block.
 *
 * Single-finger input is translated into [PlayerGesture]s by [PlayerGestureRecognizer], so taps,
 * double taps, holds and drags are decided in one place and cannot compete with each other. Two or
 * more fingers are a pinch: the recognizer suppresses its own outcomes while they are down, and the
 * pinch is forwarded to [onPinch] instead, with [onPinchEnd] once fewer than two fingers remain.
 *
 * The loop waits for the recognizer's next deadline (a long press or the end of the double-tap
 * window) and never waits on a fixed delay: a plain tap is reported the moment its window closes,
 * and a hold starts the moment the finger has been still for the long-press timeout.
 */
suspend fun AwaitPointerEventScope.detectPlayerGestures(
    longPressTimeoutMs: Long,
    doubleTapTimeoutMs: Long,
    onGesture: (PlayerGesture) -> Unit,
    onPinch: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    onPinchEnd: () -> Unit,
) {
    val recognizer = PlayerGestureRecognizer(
        longPressTimeoutMs = longPressTimeoutMs,
        doubleTapTimeoutMs = doubleTapTimeoutMs,
        touchSlopPx = viewConfiguration.touchSlop,
        sink = onGesture,
    )
    var pinching = false

    while (true) {
        val deadline = recognizer.nextDeadlineMs
        val event = if (deadline == null) {
            awaitPointerEvent()
        } else {
            withTimeoutOrNull((deadline - SystemClock.uptimeMillis()).coerceAtLeast(0L)) {
                awaitPointerEvent()
            }
        }

        if (event == null) {
            recognizer.onTimeout(SystemClock.uptimeMillis())
            continue
        }

        for (change in event.changes) {
            val id = change.id.value
            val x = change.position.x
            val y = change.position.y
            val time = change.uptimeMillis
            when {
                // A press already claimed by a control on top of the surface never starts a surface
                // gesture, matching the drag detectors this replaces (requireUnconsumed = true).
                change.changedToDown() -> if (!change.isConsumed) recognizer.onPress(id, x, y, time)
                // Releases always reach the recognizer so a tracked finger can never be left dangling.
                change.changedToUp() -> recognizer.onRelease(id, x, y, time)
                change.positionChanged() -> recognizer.onMove(id, x, y, time)
            }
        }

        val fingersDown = event.changes.count { it.pressed }
        if (fingersDown >= 2) {
            pinching = true
            val zoom = event.calculateZoom()
            val pan = event.calculatePan()
            if (zoom != 1f || pan != Offset.Zero) {
                onPinch(event.calculateCentroid(useCurrent = true), pan, zoom)
            }
            event.changes.forEach { it.consumeMotion() }
        } else {
            if (pinching) {
                pinching = false
                onPinchEnd()
            }
            // A drag or hold owns its finger, so nothing further down the tree receives its movement.
            if (recognizer.claimsMotion) {
                event.changes.forEach { it.consumeMotion() }
            }
        }
    }
}

private fun PointerInputChange.consumeMotion() {
    if (positionChanged()) consume()
}

/** The platform's default double-tap window; Compose does not expose it, so it is fixed here. */
const val DOUBLE_TAP_TIMEOUT_MS = 300L
