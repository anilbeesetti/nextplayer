package dev.anilbeesetti.nextplayer.core.data.mappers

import dev.anilbeesetti.nextplayer.core.database.entities.DirectoryEntity
import dev.anilbeesetti.nextplayer.core.model.Folder

fun DirectoryEntity.toDirectory() = Folder(
    name = name,
    path = path,
    mediaSize = size,
    mediaCount = mediaCount,
    isExcluded = false,
    dateModified = modified
)