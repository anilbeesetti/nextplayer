package dev.anilbeesetti.nextplayer.feature.player.extensions


import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride

fun Player.changeTrack(trackType: @C.TrackType Int, trackSelectionOverride: TrackSelectionOverride) {
    trackSelectionParameters = trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(trackType, false)
            .setOverrideForType(trackSelectionOverride)
            .build()
}

fun Player.disableTrack(trackType: @C.TrackType Int) {
    trackSelectionParameters = trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(trackType, true)
            .build()
}

fun Player.enableTrack(trackType: @C.TrackType Int) {
    trackSelectionParameters = trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(trackType, false)
        .build()
}
