package com.graviton.feature.player.state

import org.junit.Assert.assertEquals
import org.junit.Test

class HoldSpeedGestureTest {

    private val screenWidth = 1080f
    private val swipeThresholdPx = 10f

    @Test
    fun swipeBelowThresholdKeepsHoldSpeed() {
        val speed = HoldSpeedGesture.speedForSwipe(
            startSpeed = 2.0f,
            deltaX = 9f,
            screenWidth = screenWidth,
            swipeThresholdPx = swipeThresholdPx,
        )

        assertEquals(2.0f, speed)
    }

    @Test
    fun slideRightFromTwoTimesIncreasesByHalf() {
        val speed = HoldSpeedGesture.speedForSwipe(
            startSpeed = 2.0f,
            deltaX = pixelsForPresetSteps(1),
            screenWidth = screenWidth,
            swipeThresholdPx = swipeThresholdPx,
        )

        assertEquals(2.5f, speed)
    }

    @Test
    fun slideLeftFromTwoTimesDecreasesByHalf() {
        val speed = HoldSpeedGesture.speedForSwipe(
            startSpeed = 2.0f,
            deltaX = pixelsForPresetSteps(-1),
            screenWidth = screenWidth,
            swipeThresholdPx = swipeThresholdPx,
        )

        assertEquals(1.5f, speed)
    }

    @Test
    fun furtherSwipeSelectsAdditionalHalfSteps() {
        val faster = HoldSpeedGesture.speedForSwipe(
            startSpeed = 2.0f,
            deltaX = pixelsForPresetSteps(3),
            screenWidth = screenWidth,
            swipeThresholdPx = swipeThresholdPx,
        )
        val slower = HoldSpeedGesture.speedForSwipe(
            startSpeed = 2.0f,
            deltaX = pixelsForPresetSteps(-2),
            screenWidth = screenWidth,
            swipeThresholdPx = swipeThresholdPx,
        )

        assertEquals(3.5f, faster)
        assertEquals(1.0f, slower)
    }

    @Test
    fun swipeUsesAbsoluteDisplacementFromHoldStart() {
        val increased = HoldSpeedGesture.speedForSwipe(
            startSpeed = 2.0f,
            deltaX = pixelsForPresetSteps(2),
            screenWidth = screenWidth,
            swipeThresholdPx = swipeThresholdPx,
        )
        val restored = HoldSpeedGesture.speedForSwipe(
            startSpeed = 2.0f,
            deltaX = 0f,
            screenWidth = screenWidth,
            swipeThresholdPx = swipeThresholdPx,
        )

        assertEquals(3.0f, increased)
        assertEquals(2.0f, restored)
    }

    @Test
    fun swipeIsClampedToPresetRange() {
        val max = HoldSpeedGesture.speedForSwipe(
            startSpeed = 2.0f,
            deltaX = screenWidth,
            screenWidth = screenWidth,
            swipeThresholdPx = swipeThresholdPx,
        )
        val min = HoldSpeedGesture.speedForSwipe(
            startSpeed = 2.0f,
            deltaX = -screenWidth,
            screenWidth = screenWidth,
            swipeThresholdPx = swipeThresholdPx,
        )

        assertEquals(HoldSpeedGesture.MAX_SPEED, max)
        assertEquals(HoldSpeedGesture.MIN_SPEED, min)
    }

    @Test
    fun invalidScreenWidthKeepsHoldSpeed() {
        val speed = HoldSpeedGesture.speedForSwipe(
            startSpeed = 2.0f,
            deltaX = 200f,
            screenWidth = 0f,
            swipeThresholdPx = swipeThresholdPx,
        )

        assertEquals(2.0f, speed)
    }

    @Test
    fun presetsAboveOneUseHalfSteps() {
        val speedsFromOne = HoldSpeedGesture.SPEED_PRESETS.filter { it >= 1.0f }
        speedsFromOne.zipWithNext().forEach { (current, next) ->
            assertEquals(HoldSpeedGesture.SPEED_STEP, next - current, 0.001f)
        }
    }

    @Test
    fun formatOverlaySpeedMatchesCompactIndicator() {
        assertEquals("2", HoldSpeedGesture.formatOverlaySpeed(2.0f))
        assertEquals("1.5", HoldSpeedGesture.formatOverlaySpeed(1.5f))
        assertEquals("0.25", HoldSpeedGesture.formatOverlaySpeed(0.25f))
    }

    private fun pixelsForPresetSteps(steps: Int): Float {
        val presetsRange = HoldSpeedGesture.SPEED_PRESETS.lastIndex.toFloat()
        return ((steps + if (steps >= 0) 0.1f else -0.1f) * screenWidth) /
            (presetsRange * HoldSpeedGesture.PRESET_SWIPE_MULTIPLIER)
    }
}
