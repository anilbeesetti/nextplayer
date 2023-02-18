package dev.anilbeesetti.nextplayer.core.data.models

/**
 * Defines Player item
 * @param mediaPath path of the media
 * @param duration duration of the media
 */
data class PlayerItem(
    val mediaPath: String,
    val duration: Long
)
