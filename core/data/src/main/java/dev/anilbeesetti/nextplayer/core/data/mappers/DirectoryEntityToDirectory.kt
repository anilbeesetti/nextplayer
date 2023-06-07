package dev.anilbeesetti.nextplayer.core.data.mappers

import dev.anilbeesetti.nextplayer.core.database.entities.DirectoryEntity
import dev.anilbeesetti.nextplayer.core.model.Directory

fun DirectoryEntity.toDirectory() = Directory(
    name = name,
    path = path,
    mediaSize = size,
    mediaCount = mediaCount,
    dateModified = modified
)