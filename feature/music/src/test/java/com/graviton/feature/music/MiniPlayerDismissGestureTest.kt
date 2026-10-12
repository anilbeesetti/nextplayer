package com.graviton.feature.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlayerDismissGestureTest {

    private val barHeight = 200f

    @Test
    fun shortPullSpringsBack() {
        assertFalse(
            MiniPlayerDismissGesture.shouldDismiss(
                offsetPx = 20f,
                heightPx = barHeight,
                velocityPxPerSecond = 0f,
            ),
        )
    }

    @Test
    fun pullPastTheThresholdDismisses() {
        assertTrue(
            MiniPlayerDismissGesture.shouldDismiss(
                offsetPx = 120f,
                heightPx = barHeight,
                velocityPxPerSecond = 0f,
            ),
        )
    }

    @Test
    fun fastFlickDismissesAfterAShortTravel() {
        assertTrue(
            MiniPlayerDismissGesture.shouldDismiss(
                offsetPx = 40f,
                heightPx = barHeight,
                velocityPxPerSecond = 4000f,
                velocityThresholdPxPerSecond = 900f,
            ),
        )
    }

    @Test
    fun fastFlickThatBarelyMovedDoesNotDismiss() {
        assertFalse(
            MiniPlayerDismissGesture.shouldDismiss(
                offsetPx = 10f,
                heightPx = barHeight,
                velocityPxPerSecond = 5000f,
                velocityThresholdPxPerSecond = 900f,
            ),
        )
    }

    @Test
    fun upwardFlickNeverDismisses() {
        assertFalse(
            MiniPlayerDismissGesture.shouldDismiss(
                offsetPx = 0f,
                heightPx = barHeight,
                velocityPxPerSecond = -5000f,
            ),
        )
    }

    @Test
    fun unmeasuredBarNeverDismisses() {
        assertFalse(
            MiniPlayerDismissGesture.shouldDismiss(
                offsetPx = 150f,
                heightPx = 0f,
                velocityPxPerSecond = 5000f,
            ),
        )
    }
}
