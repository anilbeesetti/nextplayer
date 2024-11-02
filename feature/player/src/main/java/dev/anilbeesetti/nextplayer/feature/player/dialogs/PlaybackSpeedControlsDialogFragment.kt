package dev.anilbeesetti.nextplayer.feature.player.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.anilbeesetti.nextplayer.core.common.extensions.round
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR
import dev.anilbeesetti.nextplayer.feature.player.databinding.PlaybackSpeedBinding
import dev.anilbeesetti.nextplayer.feature.player.service.getSkipSilenceEnabled
import dev.anilbeesetti.nextplayer.feature.player.service.setSkipSilenceEnabled
import kotlinx.coroutines.launch

class PlaybackSpeedControlsDialogFragment(
    private val mediaController: MediaController,
) : DialogFragment() {

    private lateinit var binding: PlaybackSpeedBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = PlaybackSpeedBinding.inflate(layoutInflater)

        return activity?.let { activity ->
            binding.apply {
                val currentSpeed = mediaController.playbackParameters.speed
                speedText.text = currentSpeed.toString()
                speed.value = currentSpeed.round(1)
                lifecycleScope.launch {
                    skipSilence.isChecked = mediaController.getSkipSilenceEnabled()
                }

                speed.addOnChangeListener { _, _, _ ->
                    val newSpeed = speed.value.round(1)
                    mediaController.setPlaybackSpeed(newSpeed)
                    speedText.text = newSpeed.toString()
                }
                incSpeed.setOnClickListener {
                    if (speed.value < 4.0f) {
                        speed.value = (speed.value + 0.1f).round(1)
                    }
                }
                decSpeed.setOnClickListener {
                    if (speed.value > 0.2f) {
                        speed.value = (speed.value - 0.1f).round(1)
                    }
                }
                resetSpeed.setOnClickListener { speed.value = 1.0f }
                button02x.setOnClickListener { speed.value = 0.2f }
                button05x.setOnClickListener { speed.value = 0.5f }
                button10x.setOnClickListener { speed.value = 1.0f }
                button15x.setOnClickListener { speed.value = 1.5f }
                button20x.setOnClickListener { speed.value = 2.0f }
                button25x.setOnClickListener { speed.value = 2.5f }
                button30x.setOnClickListener { speed.value = 3.0f }
                button35x.setOnClickListener { speed.value = 3.5f }
                button40x.setOnClickListener { speed.value = 4.0f }

                skipSilence.setOnCheckedChangeListener { _, isChecked ->
                    mediaController.setSkipSilenceEnabled(isChecked)
                }
            }

            val builder = MaterialAlertDialogBuilder(activity)
            builder.setTitle(getString(coreUiR.string.select_playback_speed))
                .setView(binding.root)
                .create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}
