package com.graviton.feature.player.state

/**
 * A temporary playback-speed change that is undone when it ends.
 *
 * [start] remembers the speed the user had selected and [stop] restores exactly that value, so a
 * boost never leaves the user's configured speed at the boosted one, and never resets it to 1x.
 * [applySpeed] is the only place the playback engine is touched.
 */
class TemporarySpeedBoost(
    private val applySpeed: (Float) -> Unit,
) {
    /** True while a boost is in effect and has not yet been restored. */
    var isActive: Boolean = false
        private set

    /** The boosted speed currently applied while [isActive]; the last applied speed otherwise. */
    var activeSpeed: Float = 1f
        private set

    private var restoredSpeed = 1f

    /**
     * Boosts playback to [boostSpeed], remembering [currentSpeed] for [stop]. Starting an active boost
     * is a no-op so a second start cannot overwrite the value that must be restored.
     */
    fun start(currentSpeed: Float, boostSpeed: Float) {
        if (isActive) return
        isActive = true
        restoredSpeed = currentSpeed
        activeSpeed = boostSpeed
        applySpeed(boostSpeed)
    }

    /**
     * Changes the boosted speed while the boost is active. Returns whether the speed actually changed.
     */
    fun changeSpeed(speed: Float): Boolean {
        if (!isActive || speed == activeSpeed) return false
        activeSpeed = speed
        applySpeed(speed)
        return true
    }

    /** Restores the speed from before [start]. Safe to call when no boost is active. */
    fun stop() {
        if (!isActive) return
        isActive = false
        activeSpeed = restoredSpeed
        applySpeed(restoredSpeed)
    }
}
