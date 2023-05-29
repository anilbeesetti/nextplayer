package dev.anilbeesetti.nextplayer.feature.videopicker.extensions

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.common.extensions.getSubtitles
import dev.anilbeesetti.nextplayer.core.common.extensions.getThumbnails
import dev.anilbeesetti.nextplayer.core.model.Video
import java.io.File

val Video.uri: Uri
    get() = Uri.parse(uriString)

val Video.thumbs: List<File>
    get() = File(path).getThumbnails()

val Video.subtitleTracks: List<File>
    get() = File(path).getSubtitles()
