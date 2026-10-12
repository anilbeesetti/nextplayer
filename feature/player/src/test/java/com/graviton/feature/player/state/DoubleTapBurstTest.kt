package com.graviton.feature.player.state

import org.junit.Assert.assertEquals
import org.junit.Test

class DoubleTapBurstTest {

    private val duration = 600_000L
    private val increment = 10_000L

    @Test
    fun repeatedForwardTapsAccumulateFromTheAnchor() {
        val burst = DoubleTapBurst()
        assertEquals(70_000L, burst.step(1, increment, positionMs = 60_000L, durationMs = duration))
        assertEquals(80_000L, burst.step(1, increment, positionMs = 70_000L, durationMs = duration))
        assertEquals(90_000L, burst.step(1, increment, positionMs = 80_000L, durationMs = duration))
        assertEquals(30_000L, burst.offsetMs)
        assertEquals(90_000L, burst.targetMs)
    }

    @Test
    fun repeatedBackwardTapsAccumulateFromTheAnchor() {
        val burst = DoubleTapBurst()
        assertEquals(50_000L, burst.step(-1, increment, positionMs = 60_000L, durationMs = duration))
        assertEquals(40_000L, burst.step(-1, increment, positionMs = 50_000L, durationMs = duration))
        assertEquals(-20_000L, burst.offsetMs)
    }

    @Test
    fun turningAroundReanchorsAtTheCurrentPosition() {
        val burst = DoubleTapBurst()
        burst.step(1, increment, positionMs = 60_000L, durationMs = duration)
        burst.step(1, increment, positionMs = 70_000L, durationMs = duration)
        // Playback is now at 80 s; one backward tap goes to 70 s, not 60 s.
        assertEquals(70_000L, burst.step(-1, increment, positionMs = 80_000L, durationMs = duration))
        assertEquals(-10_000L, burst.offsetMs)
    }

    @Test
    fun aBurstClampedAtTheStartStaysClampedUntilItTurnsAround() {
        val burst = DoubleTapBurst()
        assertEquals(0L, burst.step(-1, increment, positionMs = 5_000L, durationMs = duration))
        assertEquals(-5_000L, burst.offsetMs)
        assertEquals(0L, burst.step(-1, increment, positionMs = 0L, durationMs = duration))
        assertEquals(-5_000L, burst.offsetMs)
    }

    @Test
    fun aBurstClampedAtTheEndNeverPassesTheDuration() {
        val burst = DoubleTapBurst()
        assertEquals(duration, burst.step(1, increment, positionMs = duration - 5_000L, durationMs = duration))
        assertEquals(duration, burst.step(1, increment, positionMs = duration, durationMs = duration))
        assertEquals(5_000L, burst.offsetMs)
    }

    @Test
    fun resetStartsANewBurstAtTheNewPosition() {
        val burst = DoubleTapBurst()
        burst.step(1, increment, positionMs = 60_000L, durationMs = duration)
        burst.reset()
        assertEquals(0L, burst.offsetMs)
        assertEquals(130_000L, burst.step(1, increment, positionMs = 120_000L, durationMs = duration))
    }

    @Test
    fun anUnknownDurationOnlyBoundsTheStart() {
        // -1 is what the player reports for media whose duration is not known yet (live streams).
        val burst = DoubleTapBurst()
        assertEquals(20_000L, burst.step(1, increment, positionMs = 10_000L, durationMs = -1L))
        assertEquals(30_000L, burst.step(1, increment, positionMs = 20_000L, durationMs = -1L))
        burst.reset()
        assertEquals(0L, burst.step(-1, increment, positionMs = 5_000L, durationMs = -1L))
    }
}
