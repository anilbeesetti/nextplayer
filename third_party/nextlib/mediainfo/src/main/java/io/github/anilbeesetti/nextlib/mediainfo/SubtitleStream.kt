package io.github.anilbeesetti.nextlib.mediainfo

data class SubtitleStream(
    val index: Int,
    val title: String?,
    val codecName: String,
    val language: String?,
    val disposition: Int
)