package dev.anilbeesetti.nextplayer.core.model

data class SubtitleStreamInfo(
    val index: Int,
    val title: String?,
    val codecName: String,
    val language: String?,
    val disposition: Int
)