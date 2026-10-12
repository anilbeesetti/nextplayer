package com.graviton.feature.player.gestures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerGestureRecognizerTest {

    private val events = mutableListOf<PlayerGesture>()
    private val recognizer = PlayerGestureRecognizer(
        longPressTimeoutMs = LONG_PRESS_MS,
        doubleTapTimeoutMs = DOUBLE_TAP_MS,
        touchSlopPx = SLOP_PX,
        sink = { events += it },
    )

    @Test
    fun singleTapIsReportedOnlyWhenTheDoubleTapWindowCloses() {
        recognizer.onPress(1, 100f, 100f, timeMs = 0)
        recognizer.onRelease(1, 100f, 100f, timeMs = 50)
        assertTrue(events.isEmpty())
        assertEquals(50 + DOUBLE_TAP_MS, recognizer.nextDeadlineMs)

        recognizer.onTimeout(50 + DOUBLE_TAP_MS - 1)
        assertTrue(events.isEmpty())

        recognizer.onTimeout(50 + DOUBLE_TAP_MS)
        assertEquals(listOf<PlayerGesture>(PlayerGesture.Tap(100f, 100f)), events)
        assertTrue(recognizer.isIdle)
        assertNull(recognizer.nextDeadlineMs)
    }

    @Test
    fun doubleTapFiresOnSecondReleaseEvenWhenTheSecondPressIsHeldLong() {
        press(1, 100f, 100f, 0)
        release(1, 100f, 100f, 50)
        press(2, 120f, 100f, 150)
        // Held well past the long-press timeout: must still be a double tap, never a hold.
        release(2, 120f, 100f, 150 + 2 * LONG_PRESS_MS)

        assertEquals(listOf<PlayerGesture>(PlayerGesture.DoubleTap(120f, 100f)), events)
        assertTrue(recognizer.isIdle)
    }

    @Test
    fun aLongPressStartsAHoldAndReleasingItEndsItWithoutATap() {
        press(1, 900f, 300f, 0)
        recognizer.onTimeout(LONG_PRESS_MS)
        assertEquals(listOf<PlayerGesture>(PlayerGesture.HoldStart(900f, 300f)), events)
        assertTrue(recognizer.claimsMotion)

        release(1, 900f, 300f, LONG_PRESS_MS + 200)
        assertEquals(PlayerGesture.HoldEnd, events.last())
        assertFalse(recognizer.claimsMotion)

        recognizer.onTimeout(10_000)
        assertFalse(events.any { it is PlayerGesture.Tap || it is PlayerGesture.DoubleTap })
    }

    @Test
    fun aReleaseAfterTheLongPressTimeoutWithoutAnyTimerTickStillReportsAHold() {
        press(1, 900f, 300f, 0)
        // The timeout was never delivered as its own event; the release must resolve it first.
        release(1, 900f, 300f, LONG_PRESS_MS + 100)
        assertEquals(
            listOf<PlayerGesture>(PlayerGesture.HoldStart(900f, 300f), PlayerGesture.HoldEnd),
            events,
        )
    }

    @Test
    fun movementDuringAHoldIsReportedAsHoldMoveNotAsADrag() {
        press(1, 900f, 300f, 0)
        recognizer.onTimeout(LONG_PRESS_MS)
        recognizer.onMove(1, 700f, 302f, LONG_PRESS_MS + 30)
        assertEquals(
            listOf<PlayerGesture>(PlayerGesture.HoldStart(900f, 300f), PlayerGesture.HoldMove(700f, 302f)),
            events,
        )
    }

    @Test
    fun aHorizontalDragLocksToTheHorizontalAxisAndNeverTaps() {
        press(1, 100f, 100f, 0)
        recognizer.onMove(1, 140f, 102f, 40)
        release(1, 140f, 102f, 80)

        assertEquals(
            listOf(
                PlayerGesture.DragStart(DragAxis.HORIZONTAL, 100f, 100f),
                PlayerGesture.DragMove(DragAxis.HORIZONTAL, 140f, 102f, dx = 40f, dy = 2f),
                PlayerGesture.DragEnd(DragAxis.HORIZONTAL),
            ),
            events,
        )
        recognizer.onTimeout(10_000)
        assertFalse(events.any { it is PlayerGesture.Tap })
    }

    @Test
    fun aVerticalDragLocksToTheVerticalAxis() {
        press(1, 100f, 100f, 0)
        recognizer.onMove(1, 102f, 160f, 40)
        assertEquals(PlayerGesture.DragStart(DragAxis.VERTICAL, 100f, 100f), events.first())
        assertTrue(recognizer.claimsMotion)
    }

    @Test
    fun dragMovesReportTheChangeSincethePreviousReport() {
        press(1, 100f, 100f, 0)
        recognizer.onMove(1, 130f, 100f, 20)
        recognizer.onMove(1, 150f, 100f, 40)
        assertEquals(
            PlayerGesture.DragMove(DragAxis.HORIZONTAL, 150f, 100f, dx = 20f, dy = 0f),
            events.last(),
        )
    }

    @Test
    fun aSecondTapThatDragsStartsADragAndNeverADoubleTap() {
        press(1, 100f, 100f, 0)
        release(1, 100f, 100f, 50)
        press(2, 300f, 100f, 100)
        recognizer.onMove(2, 340f, 100f, 130)
        release(2, 340f, 100f, 160)

        assertFalse(events.any { it is PlayerGesture.DoubleTap || it is PlayerGesture.Tap })
        assertEquals(PlayerGesture.DragEnd(DragAxis.HORIZONTAL), events.last())
    }

    @Test
    fun movementInsideTheSlopIsStillATap() {
        press(1, 100f, 100f, 0)
        recognizer.onMove(1, 104f, 103f, 30)
        release(1, 104f, 103f, 60)
        recognizer.onTimeout(60 + DOUBLE_TAP_MS)
        assertEquals(listOf<PlayerGesture>(PlayerGesture.Tap(100f, 100f)), events)
    }

    @Test
    fun aSmallJitterBeforeTheLongPressTimeoutStillStartsAHold() {
        press(1, 900f, 300f, 0)
        recognizer.onMove(1, 903f, 301f, 200)
        recognizer.onTimeout(LONG_PRESS_MS)
        assertEquals(listOf<PlayerGesture>(PlayerGesture.HoldStart(900f, 300f)), events)
    }

    @Test
    fun aSecondFingerDuringAHoldEndsTheHoldAndNeverTaps() {
        press(1, 900f, 300f, 0)
        recognizer.onTimeout(LONG_PRESS_MS)
        press(2, 500f, 300f, LONG_PRESS_MS + 50)
        assertEquals(PlayerGesture.HoldEnd, events.last())

        release(1, 900f, 300f, LONG_PRESS_MS + 100)
        release(2, 500f, 300f, LONG_PRESS_MS + 120)
        recognizer.onTimeout(10_000)
        assertFalse(events.any { it is PlayerGesture.Tap || it is PlayerGesture.DoubleTap })
        assertTrue(recognizer.isIdle)
    }

    @Test
    fun aPinchSuppressesEveryOutcomeUntilAllFingersAreUp() {
        press(1, 100f, 100f, 0)
        press(2, 300f, 100f, 10)
        recognizer.onMove(1, 60f, 100f, 40)
        release(1, 60f, 100f, 50)
        release(2, 300f, 100f, 60)
        recognizer.onTimeout(10_000)
        assertTrue(events.isEmpty())
        assertTrue(recognizer.isIdle)
    }

    @Test
    fun cancelEndsAHoldWithoutAProducingATap() {
        press(1, 900f, 300f, 0)
        recognizer.onTimeout(LONG_PRESS_MS)
        recognizer.onCancel()
        assertEquals(PlayerGesture.HoldEnd, events.last())
        recognizer.onTimeout(10_000)
        assertEquals(2, events.size)
    }

    @Test
    fun cancelDuringAPendingTapReportsNothing() {
        press(1, 100f, 100f, 0)
        release(1, 100f, 100f, 50)
        recognizer.onCancel()
        recognizer.onTimeout(10_000)
        assertTrue(events.isEmpty())
    }

    @Test
    fun cancelDuringAPendingPressReportsNothing() {
        press(1, 100f, 100f, 0)
        recognizer.onCancel()
        recognizer.onTimeout(10_000)
        assertTrue(events.isEmpty())
        assertTrue(recognizer.isIdle)
    }

    @Test
    fun aThirdTapAfterADoubleTapStartsAFreshGesture() {
        press(1, 100f, 100f, 0)
        release(1, 100f, 100f, 40)
        press(2, 100f, 100f, 80)
        release(2, 100f, 100f, 120)
        press(3, 100f, 100f, 160)
        release(3, 100f, 100f, 200)
        recognizer.onTimeout(200 + DOUBLE_TAP_MS)

        assertEquals(
            listOf<PlayerGesture>(PlayerGesture.DoubleTap(100f, 100f), PlayerGesture.Tap(100f, 100f)),
            events,
        )
    }

    @Test
    fun aPressTooLateForTheDoubleTapWindowIsANewTapNotASecondTap() {
        press(1, 100f, 100f, 0)
        release(1, 100f, 100f, 40)
        press(2, 100f, 100f, 40 + DOUBLE_TAP_MS + 10)
        assertEquals(listOf<PlayerGesture>(PlayerGesture.Tap(100f, 100f)), events)
        release(2, 100f, 100f, 40 + DOUBLE_TAP_MS + 20)
        recognizer.onTimeout(10_000)
        assertEquals(2, events.size)
    }

    @Test
    fun releasingAnUntrackedPointerIsIgnored() {
        press(1, 100f, 100f, 0)
        release(7, 100f, 100f, 10)
        assertFalse(recognizer.isIdle)
        assertTrue(events.isEmpty())
    }

    @Test
    fun theLongPressDeadlineIsReportedWhileAPressIsPending() {
        press(1, 100f, 100f, 1_000)
        assertEquals(1_000 + LONG_PRESS_MS, recognizer.nextDeadlineMs)
    }

    private fun press(id: Long, x: Float, y: Float, t: Long) = recognizer.onPress(id, x, y, t)

    private fun release(id: Long, x: Float, y: Float, t: Long) = recognizer.onRelease(id, x, y, t)

    private companion object {
        const val LONG_PRESS_MS = 500L
        const val DOUBLE_TAP_MS = 300L
        const val SLOP_PX = 10f
    }
}
