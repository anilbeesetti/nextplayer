package com.graviton.feature.player.gestures

import kotlin.math.abs

/** The axis a single-finger drag locked to once it moved past the touch slop. */
enum class DragAxis {
    HORIZONTAL,
    VERTICAL,
}

/**
 * What a touch sequence on the video surface turned out to be.
 *
 * Every single-finger interaction is reported as exactly one of these outcomes, so a consumer never
 * has to guess whether a press was a tap, a double tap, a hold or a drag.
 */
sealed interface PlayerGesture {
    /** One tap that was not followed by a second press within the double-tap window. */
    data class Tap(val x: Float, val y: Float) : PlayerGesture

    /** Two taps in quick succession. [x] and [y] are where the second tap was pressed. */
    data class DoubleTap(val x: Float, val y: Float) : PlayerGesture

    /** The finger stayed still past the long-press timeout. Only ever comes from a first press. */
    data class HoldStart(val x: Float, val y: Float) : PlayerGesture

    /** The held finger moved. */
    data class HoldMove(val x: Float, val y: Float) : PlayerGesture

    /** The hold ended: the finger lifted, a second finger landed, or the gesture was cancelled. */
    data object HoldEnd : PlayerGesture

    /** A drag began; [x] and [y] is the press origin the drag is measured from. */
    data class DragStart(val axis: DragAxis, val x: Float, val y: Float) : PlayerGesture

    /** The dragged finger moved; [dx] and [dy] are the change since the previous report. */
    data class DragMove(val axis: DragAxis, val x: Float, val y: Float, val dx: Float, val dy: Float) : PlayerGesture

    /** The drag ended: the finger lifted, a second finger landed, or the gesture was cancelled. */
    data class DragEnd(val axis: DragAxis) : PlayerGesture
}

/**
 * The single-finger gesture state machine for the video surface.
 *
 * It is pure Kotlin on purpose: the Compose layer only translates pointer events into calls on this
 * class, and every timing decision lives here where it can be tested without a device.
 *
 * Rules, in order of precedence:
 *
 * - A press that moves past [touchSlopPx] before the long-press timeout is a drag, and it is locked
 *   to the axis it moved along first. A drag never produces a tap.
 * - A press that stays inside the slop until [longPressTimeoutMs] elapses is a hold. A hold never
 *   produces a tap or a double tap, and it ends on release.
 * - A press released before the long-press timeout is a tap candidate. It becomes a [PlayerGesture.Tap]
 *   only if no second press arrives within [doubleTapTimeoutMs] of the release.
 * - The second press of a double tap is never a hold: released without movement it is a
 *   [PlayerGesture.DoubleTap], however long it was held.
 * - A second finger on the surface ends whatever was in progress and suppresses every outcome until
 *   all fingers are up, so pinch-to-zoom never also seeks or toggles the controls.
 *
 * Timers are not scheduled here. Callers read [nextDeadlineMs] and call [onTimeout] when that moment
 * passes; every input method also advances the timers to its own timestamp first, so a late timeout
 * can never reorder events.
 *
 * Every outcome is reported through [sink] in order, from the calling thread.
 */
class PlayerGestureRecognizer(
    private val longPressTimeoutMs: Long,
    private val doubleTapTimeoutMs: Long,
    private val touchSlopPx: Float,
    private val sink: (PlayerGesture) -> Unit,
) {
    private enum class State {
        /** Nothing is down and no tap is pending. */
        IDLE,

        /** The first finger is down and may still become a tap, a hold or a drag. */
        PRESSED,

        /** The first tap was released; waiting to see whether a second press follows. */
        AWAITING_SECOND_TAP,

        /** The second press of a double tap is down; it may still become a double tap or a drag. */
        SECOND_PRESSED,

        /** A drag owns the primary finger. */
        DRAGGING,

        /** A hold owns the primary finger. */
        HOLDING,

        /** Several fingers are involved; nothing is reported until they are all up. */
        MULTI_TOUCH,
    }

    private var state = State.IDLE
    private val activePointers = HashSet<Long>()
    private var primaryPointer = NO_POINTER
    private var originX = 0f
    private var originY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var pressedAtMs = 0L
    private var releasedAtMs = 0L
    private var dragAxis = DragAxis.HORIZONTAL

    /** True when no gesture is in progress, so a new press is a fresh gesture. */
    val isIdle: Boolean get() = state == State.IDLE

    /** True while a drag or a hold owns the primary finger; the caller should consume its movement. */
    val claimsMotion: Boolean get() = state == State.DRAGGING || state == State.HOLDING

    /** The next time [onTimeout] must be called, or null when no timer is pending. */
    val nextDeadlineMs: Long?
        get() = when (state) {
            State.PRESSED -> pressedAtMs + longPressTimeoutMs
            State.AWAITING_SECOND_TAP -> releasedAtMs + doubleTapTimeoutMs
            else -> null
        }

    /** A finger [pointerId] went down at ([x], [y]) at [timeMs]. */
    fun onPress(pointerId: Long, x: Float, y: Float, timeMs: Long) {
        onTimeout(timeMs)
        activePointers += pointerId
        when (state) {
            State.IDLE -> startGesture(State.PRESSED, pointerId, x, y, timeMs)
            State.AWAITING_SECOND_TAP -> startGesture(State.SECOND_PRESSED, pointerId, x, y, timeMs)
            else -> {
                endActiveOutcome()
                state = State.MULTI_TOUCH
            }
        }
    }

    /** A finger [pointerId] moved to ([x], [y]) at [timeMs]. Movement of other fingers is ignored. */
    fun onMove(pointerId: Long, x: Float, y: Float, timeMs: Long) {
        onTimeout(timeMs)
        if (pointerId != primaryPointer) return
        when (state) {
            State.PRESSED, State.SECOND_PRESSED -> {
                val dx = x - originX
                val dy = y - originY
                if (dx * dx + dy * dy > touchSlopPx * touchSlopPx) {
                    dragAxis = if (abs(dx) >= abs(dy)) DragAxis.HORIZONTAL else DragAxis.VERTICAL
                    state = State.DRAGGING
                    lastX = originX
                    lastY = originY
                    sink(PlayerGesture.DragStart(dragAxis, originX, originY))
                    reportDragMove(x, y)
                }
            }

            State.DRAGGING -> reportDragMove(x, y)
            State.HOLDING -> sink(PlayerGesture.HoldMove(x, y))
            else -> Unit
        }
    }

    /** A finger [pointerId] lifted at ([x], [y]) at [timeMs]. */
    fun onRelease(pointerId: Long, x: Float, y: Float, timeMs: Long) {
        onTimeout(timeMs)
        if (pointerId !in activePointers) return
        activePointers -= pointerId

        when (state) {
            State.PRESSED -> if (pointerId == primaryPointer) {
                if (isWithinSlop(x, y)) {
                    state = State.AWAITING_SECOND_TAP
                    releasedAtMs = timeMs
                } else {
                    // Lifted after leaving the slop without a move report: neither a tap nor a drag.
                    state = State.IDLE
                }
            }

            State.SECOND_PRESSED -> if (pointerId == primaryPointer) {
                if (isWithinSlop(x, y)) sink(PlayerGesture.DoubleTap(originX, originY))
                state = State.IDLE
            }

            State.DRAGGING -> if (pointerId == primaryPointer) {
                sink(PlayerGesture.DragEnd(dragAxis))
                state = State.IDLE
            }

            State.HOLDING -> if (pointerId == primaryPointer) {
                sink(PlayerGesture.HoldEnd)
                state = State.IDLE
            }

            State.MULTI_TOUCH -> if (activePointers.isEmpty()) state = State.IDLE
            State.AWAITING_SECOND_TAP, State.IDLE -> Unit
        }
        if (activePointers.isEmpty() && state == State.MULTI_TOUCH) state = State.IDLE
    }

    /** Advances the timers to [timeMs]: a long press starts, or a pending tap is resolved. */
    fun onTimeout(timeMs: Long) {
        when (state) {
            State.PRESSED -> if (timeMs - pressedAtMs >= longPressTimeoutMs) {
                state = State.HOLDING
                sink(PlayerGesture.HoldStart(originX, originY))
            }

            State.AWAITING_SECOND_TAP -> if (timeMs - releasedAtMs >= doubleTapTimeoutMs) {
                state = State.IDLE
                sink(PlayerGesture.Tap(originX, originY))
            }

            else -> Unit
        }
    }

    /**
     * Abandons the gesture without reporting a tap or a double tap: the surface lost its touch
     * stream, the player was paused, or the gesture was re-keyed. A hold or drag in progress is
     * ended so the consumer can undo its effect.
     */
    fun onCancel() {
        endActiveOutcome()
        activePointers.clear()
        primaryPointer = NO_POINTER
        state = State.IDLE
    }

    private fun startGesture(newState: State, pointerId: Long, x: Float, y: Float, timeMs: Long) {
        state = newState
        primaryPointer = pointerId
        originX = x
        originY = y
        lastX = x
        lastY = y
        pressedAtMs = timeMs
    }

    private fun reportDragMove(x: Float, y: Float) {
        sink(PlayerGesture.DragMove(dragAxis, x, y, x - lastX, y - lastY))
        lastX = x
        lastY = y
    }

    private fun isWithinSlop(x: Float, y: Float): Boolean {
        val dx = x - originX
        val dy = y - originY
        return dx * dx + dy * dy <= touchSlopPx * touchSlopPx
    }

    /** Ends a hold or drag that has already been reported as started. Taps and presses report nothing. */
    private fun endActiveOutcome() {
        when (state) {
            State.HOLDING -> sink(PlayerGesture.HoldEnd)
            State.DRAGGING -> sink(PlayerGesture.DragEnd(dragAxis))
            else -> Unit
        }
    }

    private companion object {
        const val NO_POINTER = -1L
    }
}
