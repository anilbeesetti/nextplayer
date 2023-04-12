package dev.anilbeesetti.nextplayer.core.data.mappers

import dev.anilbeesetti.nextplayer.core.data.models.Folder
import dev.anilbeesetti.nextplayer.core.data.models.Video
import dev.anilbeesetti.nextplayer.core.media.model.MediaFolder
import dev.anilbeesetti.nextplayer.core.media.model.MediaVideo
import java.io.File

fun MediaVideo.toVideo(): Video {
    val videoFile = File(data)
    return Video(
        id = id,
        path = data,
        duration = duration,
        uriString = uri.toString(),
        displayName = videoFile.nameWithoutExtension,
        nameWithExtension = videoFile.name,
        width = width,
        height = height,
        size = size
    )
}

fun MediaFolder.toFolder(): Folder {
    return Folder(
        name = name,
        path = path,
        mediaSize = media.sumOf { it.size },
        mediaCount = media.size
    )
}
