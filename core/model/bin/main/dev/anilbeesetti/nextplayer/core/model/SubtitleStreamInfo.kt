package dev.anilbeesetti.nextplayer.core.model

import java.io.Serializable

data class SubtitleStreamInfo(
    val index: Int,
    val title: String?,
    val codecName: String,
    val language: String?,
    val disposition: Int,
) : Serializable
