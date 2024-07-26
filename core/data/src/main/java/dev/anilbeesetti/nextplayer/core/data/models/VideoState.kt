package dev.anilbeesetti.nextplayer.core.data.models

import android.net.Uri

data class VideoState(
    val path: String,
    val position: Long,
    val audioTrackIndex: Int?,
    val subtitleTrackIndex: Int?,
    val playbackSpeed: Float?,
    val externalSubs: List<Uri>,
    val videoScale: Float,
)
