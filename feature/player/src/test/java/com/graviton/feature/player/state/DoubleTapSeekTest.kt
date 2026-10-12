package com.graviton.feature.player.state

import org.junit.Assert.assertEquals
import org.junit.Test

class DoubleTapSeekTest {

    private val duration = 600_000L

    @Test
    fun edgesSeekAndTheMiddleIsReservedForTheControls() {
        assertEquals(DoubleTapRegion.BACKWARD, DoubleTapSeek.regionFor(x = 0f, width = 1000f))
        assertEquals(DoubleTapRegion.BACKWARD, DoubleTapSeek.regionFor(x = 399f, width = 1000f))
        assertEquals(DoubleTapRegion.CENTER, DoubleTapSeek.regionFor(x = 400f, width = 1000f))
        assertEquals(DoubleTapRegion.CENTER, DoubleTapSeek.regionFor(x = 500f, width = 1000f))
        assertEquals(DoubleTapRegion.CENTER, DoubleTapSeek.regionFor(x = 600f, width = 1000f))
        assertEquals(DoubleTapRegion.FORWARD, DoubleTapSeek.regionFor(x = 601f, width = 1000f))
        assertEquals(DoubleTapRegion.FORWARD, DoubleTapSeek.regionFor(x = 1000f, width = 1000f))
    }

    @Test
    fun surfaceWithoutWidthNeverSeeks() {
        assertEquals(DoubleTapRegion.CENTER, DoubleTapSeek.regionFor(x = 0f, width = 0f))
    }

    @Test
    fun repeatedTapsAccumulateFromTheBurstAnchor() {
        val anchor = 60_000L

        // One tap, two taps and four taps forward from the same anchor.
        assertEquals(70_000L, DoubleTapSeek.targetFor(anchor, 10_000L, duration))
        assertEquals(80_000L, DoubleTapSeek.targetFor(anchor, 20_000L, duration))
        assertEquals(100_000L, DoubleTapSeek.targetFor(anchor, 40_000L, duration))
    }

    @Test
    fun rewindingStopsAtTheStartOfTheMedia() {
        assertEquals(0L, DoubleTapSeek.targetFor(anchorPositionMs = 5_000L, accumulatedMs = -30_000L, durationMs = duration))
    }

    @Test
    fun fastForwardingStopsAtTheEndOfTheMedia() {
        assertEquals(duration, DoubleTapSeek.targetFor(anchorPositionMs = 595_000L, accumulatedMs = 30_000L, durationMs = duration))
    }

    @Test
    fun unknownDurationOnlyBoundsTheStart() {
        // -1 is what the player reports for media whose duration is not known yet (live streams).
        assertEquals(40_000L, DoubleTapSeek.targetFor(anchorPositionMs = 10_000L, accumulatedMs = 30_000L, durationMs = -1L))
        assertEquals(0L, DoubleTapSeek.targetFor(anchorPositionMs = 10_000L, accumulatedMs = -30_000L, durationMs = -1L))
    }
}
