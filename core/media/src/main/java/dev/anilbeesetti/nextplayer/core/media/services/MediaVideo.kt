package dev.anilbeesetti.nextplayer.core.media.services

import android.net.Uri

data class MediaVideo(
    val id: Long,
    val uri: Uri,
    val path: String,
    val title: String,
    val parentPath: String,
    val displayName: String,
    val duration: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val dateModified: Long,
)
