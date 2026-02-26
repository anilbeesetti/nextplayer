package dev.anilbeesetti.nextplayer.feature.player.extensions

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import dev.anilbeesetti.nextplayer.core.common.Logger
import dev.anilbeesetti.nextplayer.feature.player.service.setMediaControllerIsScrubbingModeEnabled

/**
 * Switches to selected track.
 *
 * @param trackType The type of track to switch.
 * @param trackIndex The index of the track to switch to, or null to enable the track.
 *
 * if trackIndex is a negative number, the track will be disabled
 * if trackIndex is a valid index, the track will be switched to that index
 */
fun Player.switchTrack(trackType: @C.TrackType Int, trackIndex: Int) {
    val trackTypeText = when (trackType) {
        C.TRACK_TYPE_AUDIO -> "audio"
        C.TRACK_TYPE_TEXT -> "subtitle"
        C.TRACK_TYPE_VIDEO -> "video"
        else -> throw IllegalArgumentException("Invalid track type: $trackType")
    }

    if (trackIndex < 0) {
        if (trackType == C.TRACK_TYPE_VIDEO) {
            Logger.logDebug("Player", "Ignoring disable request for video track")
            return
        }
        Logger.logDebug("Player", "Disabling $trackTypeText")
        trackSelectionParameters = trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(trackType, true)
            .build()
    } else {
        val tracks = currentTracks.groups.filter { it.type == trackType && it.isSupported }

        if (tracks.isEmpty() || trackIndex >= tracks.size) {
            Logger.logError("Player", "Operation failed: Invalid track index: $trackIndex")
            return
        }

        Logger.logDebug("Player", "Setting $trackTypeText track: $trackIndex")
        val trackSelectionOverride = TrackSelectionOverride(tracks[trackIndex].mediaTrackGroup, 0)

        // Override the track selection parameters to force the selection of the specified track.
        trackSelectionParameters = trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(trackType, false)
            .setOverrideForType(trackSelectionOverride)
            .build()
    }
}

@UnstableApi
fun Player.getManuallySelectedTrackIndex(trackType: @C.TrackType Int): Int? {
    val isDisabled = trackSelectionParameters.disabledTrackTypes.contains(trackType)
    if (isDisabled) return -1

    val trackOverrides = trackSelectionParameters.overrides.values.map { it.mediaTrackGroup }
    val trackOverride = trackOverrides.firstOrNull { it.type == trackType } ?: return null
    val tracks = currentTracks.groups.filter { it.type == trackType }

    return tracks.indexOfFirst { it.mediaTrackGroup == trackOverride }.takeIf { it != -1 }
}

fun Player.addAdditionalSubtitleConfiguration(subtitle: MediaItem.SubtitleConfiguration) {
    val currentMediaItemLocal = currentMediaItem ?: return
    val existingSubConfigurations = currentMediaItemLocal.localConfiguration?.subtitleConfigurations ?: emptyList()

    if (existingSubConfigurations.any { it.id == subtitle.id }) {
        return
    }

    val updateMediaItem = currentMediaItemLocal
        .buildUpon()
        .setSubtitleConfigurations(existingSubConfigurations + listOf(subtitle))
        .build()

    val index = currentMediaItemIndex
    addMediaItem(index + 1, updateMediaItem)
    seekToDefaultPosition(index + 1)
    removeMediaItem(index)
}

@OptIn(UnstableApi::class)
fun Player.setIsScrubbingModeEnabled(enabled: Boolean) {
    when (this) {
        is MediaController -> this.setMediaControllerIsScrubbingModeEnabled(enabled)
        is ExoPlayer -> this.isScrubbingModeEnabled = enabled
    }
}
