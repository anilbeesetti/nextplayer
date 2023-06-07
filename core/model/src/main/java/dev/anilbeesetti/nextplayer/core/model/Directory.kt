package dev.anilbeesetti.nextplayer.core.model

data class Directory(
    val name: String,
    val path: String,
    val mediaSize: Long,
    val mediaCount: Int,
    val isExcluded: Boolean,
    val dateModified: Long
)
