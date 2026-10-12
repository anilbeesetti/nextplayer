package com.graviton.feature.player.state

/**
 * The accumulated seek of a run of double taps in one direction.
 *
 * The first tap anchors the burst at the position playback was at. Every further tap in the same
 * direction adds one increment to the offset from that anchor, so five forward taps land five
 * increments ahead of where the user started. Turning around re-anchors at the current position,
 * which is where the user actually is, instead of replaying the whole burst backwards.
 *
 * [reset] ends the burst; the caller does that once the burst's time window has closed.
 */
class DoubleTapBurst {
    /** Signed distance of the last target from the anchor; zero when no burst is active. */
    var offsetMs: Long = 0L
        private set

    /** Where the last step seeks to. */
    var targetMs: Long = 0L
        private set

    private var anchorMs: Long? = null
    private var direction = 0

    /**
     * Adds one [incrementMs] step in [direction] (+1 forward, -1 backward) and returns the position
     * to seek to. The result is clamped to the media, see [DoubleTapSeek.targetFor].
     */
    fun step(direction: Int, incrementMs: Long, positionMs: Long, durationMs: Long): Long {
        require(direction == 1 || direction == -1) { "direction must be +1 or -1, was $direction" }
        if (anchorMs == null || this.direction != direction) {
            anchorMs = positionMs
            this.direction = direction
            offsetMs = 0L
        }
        val burstAnchor = checkNotNull(anchorMs)
        val target = DoubleTapSeek.targetFor(
            anchorPositionMs = burstAnchor,
            accumulatedMs = offsetMs + direction * incrementMs,
            durationMs = durationMs,
        )
        offsetMs = target - burstAnchor
        targetMs = target
        return target
    }

    /** Ends the burst so the next double tap starts a new one. */
    fun reset() {
        anchorMs = null
        direction = 0
        offsetMs = 0L
        targetMs = 0L
    }
}
