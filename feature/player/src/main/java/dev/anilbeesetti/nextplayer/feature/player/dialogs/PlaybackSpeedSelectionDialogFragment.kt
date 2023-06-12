package dev.anilbeesetti.nextplayer.feature.player.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR

class PlaybackSpeedSelectionDialogFragment(
    private val currentSpeed: Float,
    private val onChange: (Float) -> Unit
//    private val player: Player
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val speedTexts = listOf("0.5x", "0.75x", "1x", "1.25x", "1.5x", "1.75x", "2x")
        val speedNumbers = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

        return activity?.let { activity ->
            val builder = MaterialAlertDialogBuilder(activity)
            builder.setTitle(getString(coreUiR.string.select_playback_speed))
                .setSingleChoiceItems(
                    speedTexts.toTypedArray(),
                    speedNumbers.indexOf(currentSpeed)
                ) { dialog, which ->
                    onChange(speedNumbers[which])
                    dialog.dismiss()
                }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}
