package dev.anilbeesetti.nextplayer.core.datastore

import kotlinx.serialization.Serializable

@Serializable
data class PlayerPreferences(
    val resume: Resume = Resume.YES,
    val rememberPlayerBrightness: Boolean = false,
    val playerBrightness: Float = 0.5f,
    val doubleTapGesture: DoubleTapGesture = DoubleTapGesture.FAST_FORWARD_AND_REWIND
)

enum class Resume(val value: String) {
    YES(value = "Yes"),
    NO(value = "No")
}

enum class DoubleTapGesture(val value: String) {
    PLAY_PAUSE(value = "Play/Pause"),
    FAST_FORWARD_AND_REWIND(value = "Fast Forward/Rewind"),
    NONE(value = "None")
}
