package dev.anilbeesetti.nextplayer.core.model

enum class DoubleTapGesture(val value: String) {
    PLAY_PAUSE(value = "Play/Pause"),
    FAST_FORWARD_AND_REWIND(value = "Fast forward/Rewind"),
    BOTH(value = "Play/Pause and Fast forward/Rewind"),
    NONE(value = "None")
}