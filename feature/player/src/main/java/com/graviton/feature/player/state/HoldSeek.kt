package com.graviton.feature.player.state

/**
 * Seek maths for the hold-to-seek gesture.
 *
 * Holding a side of the video seeks by the configured increment immediately, so the first feedback
 * is the same ±N seconds the user configured for double taps. From there the gesture accelerates:
 * every [STEP_DURATION_MS] the rate steps up through [RATES] (media seconds per second of holding)
 * until it settles at the last entry, which is the ceiling the gesture is allowed to reach. Holding
 * for a full five seconds therefore covers roughly three minutes of media without ever turning into
 * an uncontrolled jump.
 *
 * Everything here is pure arithmetic: the gesture state calls [deltaAt] with the elapsed hold time
 * on every tick, so the displayed amount stays a function of how long the finger has been down
 * rather than an accumulation of per-frame noise.
 */
object HoldSeek {
    /** Media seconds advanced per second of holding, per ramp step. */
    private val RATES = floatArrayOf(3f, 5f, 10f, 20f, 40f)

    /** How long each rate is used before the gesture moves to the next one. */
    const val STEP_DURATION_MS = 800L

    /** Upper bound for a single hold, so an unknown duration can never produce an absurd target. */
    const val MAX_DELTA_MS = 30 * 60 * 1000L

    /**
     * The seek offset for a hold that has lasted [elapsedMs] with the user's [incrementMs] applied
     * as the very first step. Always positive: the caller applies the direction.
     */
    fun deltaAt(elapsedMs: Long, incrementMs: Long): Long {
        val elapsed = elapsedMs.coerceIn(0L, MAX_HOLD_MS)
        var remaining = elapsed
        var delta = incrementMs.coerceAtLeast(0L).toFloat()
        var step = 0
        while (remaining > 0L) {
            val span = minOf(remaining, STEP_DURATION_MS)
            delta += RATES[step.coerceAtMost(RATES.lastIndex)] * span / 1000f
            remaining -= span
            step++
        }
        return delta.toLong().coerceIn(0L, MAX_DELTA_MS)
    }

    /** The position of the current rate in [RATES]; used to fire one haptic tick per step. */
    fun tierAt(elapsedMs: Long): Int = (elapsedMs.coerceIn(0L, MAX_HOLD_MS) / STEP_DURATION_MS).toInt().coerceAtMost(RATES.lastIndex)

    /**
     * Which way a hold at [x] seeks, or null when the press is too close to the middle of the
     * video to be an intentional side gesture.
     */
    fun directionFor(x: Float, width: Float): Int? {
        if (width <= 0f) return null
        val fraction = x / width
        return when {
            fraction < DEAD_ZONE_START -> -1
            fraction > DEAD_ZONE_END -> 1
            else -> null
        }
    }

    /** The middle tenth of the video is shared with the play/pause double tap, so it never seeks. */
    private const val DEAD_ZONE_START = 0.45f
    private const val DEAD_ZONE_END = 0.55f

    private const val MAX_HOLD_MS = 60_000L
}
