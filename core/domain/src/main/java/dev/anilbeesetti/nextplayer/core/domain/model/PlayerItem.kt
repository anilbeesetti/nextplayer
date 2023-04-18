package dev.anilbeesetti.nextplayer.core.domain.model

import dev.anilbeesetti.nextplayer.core.common.extensions.getSubtitles
import java.io.File

data class PlayerItem(
    val path: String,
    val uriString: String,
    val duration: Long
) {
    val subtitleTracks: List<File>
        get() = File(path).getSubtitles()
}
