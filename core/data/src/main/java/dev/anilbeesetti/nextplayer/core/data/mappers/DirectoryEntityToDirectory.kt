package dev.anilbeesetti.nextplayer.core.data.mappers

import dev.anilbeesetti.nextplayer.core.common.Utils
import dev.anilbeesetti.nextplayer.core.database.entities.DirectoryEntity
import dev.anilbeesetti.nextplayer.core.model.Folder

fun DirectoryEntity.toFolder() = Folder(
    name = name,
    path = path,
    mediaSize = size,
    mediaCount = mediaCount,
    dateModified = modified,
    formattedMediaSize = Utils.formatFileSize(size)
)
