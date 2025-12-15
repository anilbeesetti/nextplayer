package io.github.anilbeesetti.nextlib.mediainfo

data class VideoStream(
    val index: Int,
    val title: String?,
    val codecName: String,
    val language: String?,
    val disposition: Int,
    val bitRate: Long,
    val frameRate: Double,
    val frameWidth: Int,
    val frameHeight: Int,
    val rotation: Int,
)