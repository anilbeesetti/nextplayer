package dev.anilbeesetti.nextplayer.core.domain.model

import java.io.File

data class PlayerItem(
    val path: String,
    val duration: Long,
    val subtitleTracks: List<File>
)
