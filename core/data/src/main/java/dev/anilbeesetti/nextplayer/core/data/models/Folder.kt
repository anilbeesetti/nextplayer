package dev.anilbeesetti.nextplayer.core.data.models

data class Folder(
    val id: Long,
    val name: String,
    val path: String,
    val mediaSize: Long,
    val mediaCount: Int
)
