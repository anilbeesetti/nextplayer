package dev.anilbeesetti.nextplayer.core.data.models

/**
 * Defines Player item
 * @param path path of the media
 * @param duration duration of the media
 */
data class PlayerItem(
    val path: String,
    val duration: Long
)
