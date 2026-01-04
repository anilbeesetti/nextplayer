package dev.anilbeesetti.nextplayer.feature.player.extensions

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs

/**
 * Detects custom transform gestures including pan, zoom, and rotation gestures.
 * This function allows customization of gesture handling and locks gestures to pan and zoom if specified.
 * This is a custom implementation of the [androidx.compose.foundation.gestures.detectTransformGestures] function that allows for more granular control over the gestures.
 *
 * https://stackoverflow.com/questions/76370595/how-to-handle-horizontal-scroll-gesture-combined-with-transform-gestures-in-jetp/76374985#76374985
 *
 * @param panZoomLock If true, locks gestures to pan and zoom, ignoring rotation if the touch-slope threshold for rotation is not exceeded.
 * @param consume If true, consumes the detected gesture changes to prevent other gesture detectors from handling them.
 * @param pass Specifies the type of pointer events to process (e.g., Main, Initial).
 * @param onGestureStart Function invoked when a gesture is initiated with the first down event. The function receives the initial pointer change.
 * @param onGesture Function invoked during each gesture update with details on the gesture including the centroid position, pan offset, zoom factor, rotation angle, the current primary
 *  pointer change, and the list of all pointer changes.
 * @param onGestureEnd Function invoked when the gesture ends. The function receives the final pointer change.
 */
suspend fun PointerInputScope.detectCustomTransformGestures(
    panZoomLock: Boolean = false,
    pass: PointerEventPass = PointerEventPass.Main,
    pointCount: Int = 2,
    onGestureStart: (PointerInputChange) -> Unit = {},
    onGesture: (
        centroid: Offset,
        pan: Offset,
        zoom: Float,
        rotation: Float,
    ) -> Unit,
    onGestureEnd: (PointerInputChange) -> Unit = {},
) {
    awaitEachGesture {
        var rotation = 0f
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop
        var lockedToPanZoom = false
        var gestureStarted = false

        // Wait for at least one pointer to press down and set the first contact position
        val down: PointerInputChange = awaitFirstDown(
            requireUnconsumed = false,
            pass = pass,
        )

        var pointer = down
        // The main pointer is the one that is down initially
        var pointerId = down.id

        do {
            val event = awaitPointerEvent(pass = pass)

            // Count the number of pointers currently down
            val currentPointerCount = event.changes.count { it.pressed }

            // If any position change is consumed from another PointerInputChange
            // or pointer count requirement is not fulfilled
            val canceled = event.changes.any { it.isConsumed } || currentPointerCount != pointCount

            if (!canceled) {
                // Trigger onGestureStart only once when pointer count requirement is met
                if (!gestureStarted) {
                    gestureStarted = true
                    onGestureStart(pointer)
                }

                // Get a pointer that is down if the first pointer is up,
                // get another and use it if other pointers are also down
                // event.changes.first() doesn't return same order
                val pointerInputChange = event.changes.firstOrNull { it.id == pointerId }
                    ?: event.changes.first()

                // Next time will check the same pointer with this id
                pointerId = pointerInputChange.id
                pointer = pointerInputChange

                val zoomChange = event.calculateZoom()
                val rotationChange = event.calculateRotation()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    rotation += rotationChange
                    pan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val rotationMotion = abs(
                        rotation * kotlin.math.PI.toFloat() * centroidSize / 180f,
                    )
                    val panMotion = pan.getDistance()

                    if (zoomMotion > touchSlop ||
                        rotationMotion > touchSlop ||
                        panMotion > touchSlop
                    ) {
                        pastTouchSlop = true
                        lockedToPanZoom = panZoomLock && rotationMotion < touchSlop
                    }
                }

                if (pastTouchSlop) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    val effectiveRotation = if (lockedToPanZoom) 0f else rotationChange
                    if (effectiveRotation != 0f ||
                        zoomChange != 1f ||
                        panChange != Offset.Zero
                    ) {
                        onGesture(
                            centroid,
                            panChange,
                            zoomChange,
                            effectiveRotation,
                        )
                    }

                    event.changes.forEach {
                        if (it.positionChanged()) {
                            it.consume()
                        }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })

        // Only trigger onGestureEnd if gesture was actually started
        if (gestureStarted) {
            onGestureEnd(pointer)
        }
    }
}
