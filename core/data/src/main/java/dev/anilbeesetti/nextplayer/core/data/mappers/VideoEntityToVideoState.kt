package dev.anilbeesetti.nextplayer.core.data.mappers

import dev.anilbeesetti.nextplayer.core.data.models.VideoState
import dev.anilbeesetti.nextplayer.core.database.entities.VideoEntity

fun VideoEntity.toVideoState(): VideoState {
    return VideoState(
        path = path,
        position = playbackPosition,
        audioTrack = audioTrack,
        subtitleTrack = subtitleTrack,
        playbackSpeed = playbackSpeed
    )
}
