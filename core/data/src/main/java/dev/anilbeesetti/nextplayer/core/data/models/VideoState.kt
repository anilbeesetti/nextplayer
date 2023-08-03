package dev.anilbeesetti.nextplayer.core.data.models

data class VideoState(
    val path: String,
    val position: Long,
    val audioTrackIndex: Int?,
    val subtitleTrackIndex: Int?,
    val playbackSpeed: Float?
)
