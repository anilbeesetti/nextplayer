package dev.anilbeesetti.nextplayer.core.data.mappers

import dev.anilbeesetti.nextplayer.core.data.models.VideoState
import dev.anilbeesetti.nextplayer.core.database.entities.MediumEntity
import dev.anilbeesetti.nextplayer.core.model.Video

fun MediumEntity.toVideoState(): VideoState {
    return VideoState(
        path = path,
        position = playbackPosition,
        audioTrack = audioTrackIndex,
        subtitleTrack = subtitleTrackIndex,
        playbackSpeed = playbackSpeed
    )
}

fun MediumEntity.toVideo() = Video(
    id = mediaStoreId,
    path = path,
    duration = duration,
    uriString = "",
    displayName = name,
    nameWithExtension = name,
    width = width,
    height = height,
    size = size,
    dateModified = 0,
    formattedDuration = "",
    formattedFileSize = "",
    thumbnail = null
)
