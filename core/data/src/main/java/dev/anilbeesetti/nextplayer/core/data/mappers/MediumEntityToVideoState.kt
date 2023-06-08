package dev.anilbeesetti.nextplayer.core.data.mappers

import dev.anilbeesetti.nextplayer.core.data.models.VideoState
import dev.anilbeesetti.nextplayer.core.database.entities.MediumEntity

fun MediumEntity.toVideoState(): VideoState {
    return VideoState(
        path = path,
        position = playbackPosition,
        audioTrack = audioTrackIndex,
        subtitleTrack = subtitleTrackIndex,
        playbackSpeed = playbackSpeed
    )
}
