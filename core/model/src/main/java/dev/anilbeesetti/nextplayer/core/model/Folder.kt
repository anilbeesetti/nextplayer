package dev.anilbeesetti.nextplayer.core.model

data class Folder(
    val name: String,
    val path: String,
    val mediaSize: Long,
    val mediaCount: Int,
    val isExcluded: Boolean
)
