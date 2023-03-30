package dev.anilbeesetti.nextplayer.core.data.models

data class VideoState(
    val path: String,
    val position: Long,
    val audioTrack: Int?,
    val subtitleTrack: Int?
)
