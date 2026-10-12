package com.graviton.feature.player.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldSeekTest {

    private val increment = 10_000L

    @Test
    fun holdStartsWithExactlyOneIncrement() {
        assertEquals(increment, HoldSeek.deltaAt(elapsedMs = 0L, incrementMs = increment))
    }

    @Test
    fun holdAcceleratesWithoutEverGoingBackwards() {
        var previous = 0L
        var elapsed = 0L
        while (elapsed <= 20_000L) {
            val delta = HoldSeek.deltaAt(elapsedMs = elapsed, incrementMs = increment)
            assertTrue("delta shrank at ${elapsed}ms", delta >= previous)
            previous = delta
            elapsed += 100L
        }

        assertTrue(previous > increment)
    }

    @Test
    fun holdIsCapped() {
        val delta = HoldSeek.deltaAt(elapsedMs = 10 * 60_000L, incrementMs = increment)
        assertEquals(HoldSeek.MAX_DELTA_MS, delta)
    }

    @Test
    fun eachRampStepAddsMoreThanThePreviousOne() {
        val firstStep = HoldSeek.deltaAt(HoldSeek.STEP_DURATION_MS, increment) -
            HoldSeek.deltaAt(0L, increment)
        val secondStep = HoldSeek.deltaAt(HoldSeek.STEP_DURATION_MS * 2, increment) -
            HoldSeek.deltaAt(HoldSeek.STEP_DURATION_MS, increment)

        assertTrue("second step must be faster than the first", secondStep > firstStep)
    }

    @Test
    fun tierAdvancesOncePerRampStep() {
        assertEquals(0, HoldSeek.tierAt(0L))
        assertEquals(1, HoldSeek.tierAt(HoldSeek.STEP_DURATION_MS))
        assertEquals(2, HoldSeek.tierAt(HoldSeek.STEP_DURATION_MS * 2))
    }

    @Test
    fun sidesSeekInOppositeDirectionsAndTheMiddleDoesNotSeek() {
        assertEquals(-1, HoldSeek.directionFor(x = 100f, width = 1000f))
        assertEquals(1, HoldSeek.directionFor(x = 900f, width = 1000f))
        assertNull(HoldSeek.directionFor(x = 500f, width = 1000f))
    }

    @Test
    fun unmeasurableSurfaceNeverSeeks() {
        assertNull(HoldSeek.directionFor(x = 0f, width = 0f))
    }
}
