package dev.anilbeesetti.nextplayer.feature.player.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.media3.common.Player
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR
import dev.anilbeesetti.nextplayer.feature.player.PlayerViewModel

class PlaybackSpeedSelectionDialogFragment(
    private val player: Player
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val speedTexts = listOf("0.5x", "0.75x", "1x", "1.25x", "1.5x", "1.75x", "2x")
        val speedNumbers = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

        return activity?.let { activity ->
            val builder = MaterialAlertDialogBuilder(activity)
            builder.setTitle(getString(coreUiR.string.select_playback_speed))
                .setSingleChoiceItems(
                    speedTexts.toTypedArray(),
                    speedNumbers.indexOf(player.playbackParameters.speed)
                ) { dialog, which ->
                    player.setPlaybackSpeed(speedNumbers[which])
                    dialog.dismiss()
                }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}



