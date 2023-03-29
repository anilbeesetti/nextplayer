package dev.anilbeesetti.nextplayer.feature.player.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.media3.common.C
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.anilbeesetti.nextplayer.feature.player.PlayerViewModel
import dev.anilbeesetti.nextplayer.feature.player.R
import java.util.Locale

@UnstableApi
class TrackSelectionFragment(
    private val type: @C.TrackType Int,
    private val tracks: Tracks,
    private val viewModel: PlayerViewModel
): DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
       when (type) {
           C.TRACK_TYPE_AUDIO -> {
               return activity?.let { activity ->
                   val audioTracks = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                   val trackNames = audioTracks.filter { it.isSupported }.mapIndexed { index, trackGroup ->
                       trackGroup.mediaTrackGroup.getName(type, index)
                   }.toTypedArray()
                   MaterialAlertDialogBuilder(activity)
                       .setTitle(getString(R.string.select_audio_track))
                       .setSingleChoiceItems(
                           trackNames,
                           audioTracks.indexOfFirst { it.isSelected }
                       ) { dialog, trackIndex ->
                           viewModel.switchTrackToIndex(type, trackIndex)
                           dialog.dismiss()
                       }
                       .create()
               } ?: throw IllegalStateException("Activity cannot be null")
           }
           C.TRACK_TYPE_TEXT -> {
               return activity?.let { activity ->
                   val textTracks = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                   val trackNames = textTracks.filter { it.isSupported }.mapIndexed { index, trackGroup ->
                       trackGroup.mediaTrackGroup.getName(type, index)
                   }.toTypedArray()
                   MaterialAlertDialogBuilder(activity)
                       .setTitle(getString(R.string.select_subtitle_track))
                       .setSingleChoiceItems(
                           trackNames,
                           textTracks.indexOfFirst { it.isSelected }
                       ) { dialog, trackIndex ->
                           viewModel.switchTrackToIndex(type, trackIndex)
                           dialog.dismiss()
                       }
                       .create()
               } ?: throw IllegalStateException("Activity cannot be null")
           }
           else -> {
               throw IllegalArgumentException("Track type not supported. Track type must be either TRACK_TYPE_AUDIO or TRACK_TYPE_TEXT")
           }
       }
    }
}

@UnstableApi
fun TrackGroup.getName(trackType: @C.TrackType Int,index: Int): String? {
    val format = this.getFormat(0)
    val language = format.language
    val label = format.label
    return buildString {
        if (label != null) {
            append(label)
        }
        if (language != null && language != "und") {
            if (label != null) {
                append(" - ")
            }
            append(Locale(language).displayLanguage)
        }
        if (isEmpty()) {
            if (trackType == C.TRACK_TYPE_TEXT) {
                append("Subtitle Track #${index + 1}")
            } else {
                append("Audio Track #${index + 1}")
            }
        }
    }
}