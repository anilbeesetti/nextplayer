package com.graviton.feature.player.state

import java.util.Locale
import kotlin.math.abs

/**
 * Hold-to-speed math from mpvRex's GestureHandler.
 *
 * A long-press starts temporary speed control at the configured hold speed. Horizontal
 * movement then selects the nearest preset: right increases, left decreases. The 0.5×
 * steps above 1× (1.0, 1.5, 2.0, …, 4.0) are the same table mpvRex uses.
 */
object HoldSpeedGesture {
    val SPEED_PRESETS = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f)

    const val SWIPE_THRESHOLD_DP = 10f
    const val PRESET_SWIPE_MULTIPLIER = 3.5f
    const val MIN_SPEED = 0.25f
    const val MAX_SPEED = 4.0f
    const val SPEED_STEP = 0.5f

    fun speedForSwipe(
        startSpeed: Float,
        deltaX: Float,
        screenWidth: Float,
        swipeThresholdPx: Float,
    ): Float {
        if (abs(deltaX) < swipeThresholdPx) return startSpeed
        if (screenWidth <= 0f) return startSpeed

        val presetsRange = SPEED_PRESETS.lastIndex
        val indexDelta = (deltaX / screenWidth) * presetsRange * PRESET_SWIPE_MULTIPLIER
        val startIndex = nearestPresetIndex(startSpeed)
        val newIndex = (startIndex + indexDelta.toInt()).coerceIn(0, SPEED_PRESETS.lastIndex)
        return SPEED_PRESETS[newIndex]
    }

    fun nearestPresetIndex(speed: Float): Int =
        SPEED_PRESETS.indices.minBy { abs(SPEED_PRESETS[it] - speed) }

    fun formatOverlaySpeed(speed: Float): String = when {
        speed % 1f == 0f -> speed.toInt().toString()
        speed * 10f % 1f == 0f -> "%.1f".format(Locale.US, speed)
        else -> "%.2f".format(Locale.US, speed)
    }
}
