package dev.anilbeesetti.nextplayer.core.data.mappers

import dev.anilbeesetti.nextplayer.core.common.Utils
import dev.anilbeesetti.nextplayer.core.database.relations.DirectoryWithMedia
import dev.anilbeesetti.nextplayer.core.model.Folder

fun DirectoryWithMedia.toFolder() = Folder(
    name = directory.name,
    path = directory.path,
    mediaSize = media.sumOf { it.size },
    mediaCount = media.size,
    dateModified = directory.modified,
    formattedMediaSize = Utils.formatFileSize(media.sumOf { it.size }),
    mediaList = media.map { it.toVideo() },
)
