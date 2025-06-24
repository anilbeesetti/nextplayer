package dev.anilbeesetti.nextplayer.core.model

import java.io.Serializable

data class VideoStreamInfo(
    val index: Int,
    val title: String?,
    val codecName: String,
    val language: String?,
    val disposition: Int,
    val bitRate: Long,
    val frameRate: Double,
    val frameWidth: Int,
    val frameHeight: Int,
) : Serializable
