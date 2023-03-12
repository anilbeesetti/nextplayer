package dev.anilbeesetti.nextplayer.core.media.model

import android.net.Uri

data class MediaVideo(
    val id: Long,
    val uri: Uri,
    val width: Int,
    val height: Int,
    val data: String,
    val duration: Long
)
