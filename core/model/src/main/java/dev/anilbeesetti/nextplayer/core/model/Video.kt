package dev.anilbeesetti.nextplayer.core.model

data class Video(
    val id: Long,
    val path: String,
    val duration: Long,
    val uriString: String,
    val displayName: String,
    val nameWithExtension: String,
    val width: Int,
    val height: Int,
    val size: Long
)
