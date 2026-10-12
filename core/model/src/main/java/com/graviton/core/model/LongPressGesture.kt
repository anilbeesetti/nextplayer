package com.graviton.core.model

/**
 * What holding a finger on the video surface does while the finger stays down.
 *
 * [SEEK] is the hold-to-seek interaction: holding the left side of the video rewinds and holding
 * the right side fast-forwards, accelerating the longer the finger stays down, with the resulting
 * target shown in an overlay until the finger is lifted.
 *
 * [SPEED_BOOST] is Graviton's original hold behaviour: playback continues at
 * [PlayerPreferences.longPressControlsSpeed] for as long as the finger stays down.
 */
enum class LongPressGesture {
    SEEK,
    SPEED_BOOST,
    NONE,
}

/**
 * The long press behaviour that is actually active. [PlayerPreferences.useLongPressControls] stays
 * the master switch it has always been, so a long press the user turned off stays off.
 */
val PlayerPreferences.longPressAction: LongPressGesture
    get() = if (useLongPressControls) longPressGesture else LongPressGesture.NONE
