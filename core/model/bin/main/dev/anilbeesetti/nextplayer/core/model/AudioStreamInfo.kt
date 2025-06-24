package dev.anilbeesetti.nextplayer.core.model

import java.io.Serializable

data class AudioStreamInfo(
    val index: Int,
    val title: String?,
    val codecName: String,
    val language: String?,
    val disposition: Int,
    val bitRate: Long,
    val sampleFormat: String?,
    val sampleRate: Int,
    val channels: Int,
    val channelLayout: String?,
) : Serializable
