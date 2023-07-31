package dev.anilbeesetti.nextplayer.core.data.mappers

import dev.anilbeesetti.nextplayer.core.common.Utils
import dev.anilbeesetti.nextplayer.core.database.entities.MediumEntity
import dev.anilbeesetti.nextplayer.core.model.Video
import java.io.File

fun MediumEntity.toVideo() = Video(
    id = mediaStoreId,
    path = path,
    parentPath = parentPath,
    duration = duration,
    uriString = uriString,
    displayName = name.substringBeforeLast("."),
    nameWithExtension = name,
    width = width,
    height = height,
    size = size,
    dateModified = modified,
    formattedDuration = Utils.formatDurationMillis(duration),
    formattedFileSize = Utils.formatFileSize(size)
)
