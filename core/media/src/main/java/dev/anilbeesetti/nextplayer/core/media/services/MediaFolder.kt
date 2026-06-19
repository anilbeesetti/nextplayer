package dev.anilbeesetti.nextplayer.core.media.services

data class MediaFolder(
    val path: String,
    val name: String,
    val dateModified: Long,
    val totalSize: Long,
    val totalDuration: Long,
    val videosCount: Int,
    val foldersCount: Int,
)
