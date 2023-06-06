package dev.anilbeesetti.nextplayer.core.data.mappers

import dev.anilbeesetti.nextplayer.core.common.Utils
import dev.anilbeesetti.nextplayer.core.media.model.MediaVideo
import dev.anilbeesetti.nextplayer.core.model.Video
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
        size = size,
        formattedFileSize = Utils.formatFileSize(size),
        formattedDuration = Utils.formatDurationMillis(duration)
    )
}
