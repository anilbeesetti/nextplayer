package dev.anilbeesetti.nextplayer.core.datastore

import kotlinx.serialization.Serializable

@Serializable
data class PlayerPreferences(
    val resume: Resume = Resume.YES,
    val rememberPlayerBrightness: Boolean = false,
    val playerBrightness: Float = 0.5f,
    val doubleTapGesture: DoubleTapGesture = DoubleTapGesture.FAST_FORWARD_AND_REWIND,
    val fastSeek: FastSeek = FastSeek.AUTO,
    val minDurationForFastSeek: Long = 120000L,
    val useSwipeControls: Boolean = true,
    val useSeekControls: Boolean = true,
    val rememberSelections: Boolean = true
)

enum class Resume(val value: String) {
    YES(value = "Yes"),
    NO(value = "No")
}

enum class FastSeek(val value: String) {
    AUTO(value = "Auto"),
    ENABLE(value = "Enable"),
    DISABLE(value = "Disable")
}

enum class DoubleTapGesture(val value: String) {
    PLAY_PAUSE(value = "Play/Pause"),
    FAST_FORWARD_AND_REWIND(value = "Fast forward/Rewind"),
    BOTH(value = "Play/Pause and Fast forward/Rewind"),
    NONE(value = "None")
}
