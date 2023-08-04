package dev.anilbeesetti.nextplayer.feature.player.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.anilbeesetti.nextplayer.feature.player.databinding.PlaybackSpeedBinding
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR

class PlaybackSpeedSelectionDialogFragment(
    private val currentSpeed: Float,
    private val onChange: (Float) -> Unit
) : DialogFragment() {

    lateinit var binding: PlaybackSpeedBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = PlaybackSpeedBinding.inflate(layoutInflater)

        return activity?.let { activity ->

            binding.apply {
                speed.value = currentSpeed
                speedText.text = currentSpeed.toString()
                speed.addOnChangeListener { _, _, _ ->
                    val newSpeed = String.format("%.1f", speed.value).toFloat()
                    onChange(newSpeed)
                    speedText.text = newSpeed.toString()
                }
                resetSpeed.setOnClickListener {
                    speed.value = 1.0f
                }
                incSpeed.setOnClickListener {
                    if (speed.value < 4.0f) {
                        speed.value += 0.1f
                    }
                }
                decSpeed.setOnClickListener {
                    if (speed.value > 0.2f) {
                        speed.value -= 0.1f
                    }
                }
            }


            val builder = MaterialAlertDialogBuilder(activity)
            builder.setTitle(getString(coreUiR.string.select_playback_speed))
                .setView(binding.root)
                .create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}
