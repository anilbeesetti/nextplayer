package com.graviton.feature.player.state

/** The three touch regions of the video surface that a double tap can land in. */
enum class DoubleTapRegion {
    BACKWARD,
    FORWARD,

    /** The middle of the video: reserved for play/pause and the controls, never for seeking. */
    CENTER,
}

/**
 * Seek maths for double tap seeking.
 *
 * A tap outside the middle [EDGE_FRACTION] of the width seeks; everything between the two edges
 * belongs to the playback controls, so a double tap in the centre of the picture can never move the
 * playhead by accident.
 *
 * Repeated taps form a burst: they are measured from [BURST_TIMEOUT_MS]-long windows anchored at the
 * position the burst started from, so five taps forward land exactly five increments ahead of where
 * the user began instead of drifting with each individual seek.
 */
object DoubleTapSeek {
    /** How much of the width on each side counts as a seek edge. */
    const val EDGE_FRACTION = 0.4f

    /** Taps closer together than this keep adding to the same burst. */
    const val BURST_TIMEOUT_MS = 800L

    /** Which region of a surface [width] pixels wide a touch at [x] landed in. */
    fun regionFor(x: Float, width: Float): DoubleTapRegion {
        if (width <= 0f) return DoubleTapRegion.CENTER
        val fraction = x / width
        return when {
            fraction < EDGE_FRACTION -> DoubleTapRegion.BACKWARD
            fraction > 1f - EDGE_FRACTION -> DoubleTapRegion.FORWARD
            else -> DoubleTapRegion.CENTER
        }
    }

    /**
     * The position a burst anchored at [anchorPositionMs] seeks to after [accumulatedMs] of signed
     * seek offsets. The result is clamped to the media: never before the start, and never past the
     * duration when it is known (an unknown duration — live or still resolving media — only bounds
     * the start).
     */
    fun targetFor(anchorPositionMs: Long, accumulatedMs: Long, durationMs: Long): Long {
        val raw = anchorPositionMs + accumulatedMs
        val upperBound = if (durationMs > 0L) durationMs else Long.MAX_VALUE
        return raw.coerceIn(0L, upperBound)
    }
}
