package dev.anilbeesetti.nextplayer.feature.player.model

import androidx.annotation.StringRes
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderMode

val selectableDecoderModes = listOf(
    DecoderMode.HARDWARE,
    DecoderMode.HW_PLUS,
    DecoderMode.FFMPEG,
)

@get:StringRes
val DecoderMode.labelRes: Int
    get() = when (this) {
        DecoderMode.AUTO -> R.string.decoder_mode_auto
        DecoderMode.HARDWARE -> R.string.decoder_mode_hardware
        DecoderMode.HW_PLUS -> R.string.decoder_mode_hardware_plus
        DecoderMode.FFMPEG -> R.string.decoder_mode_app_software
    }

@get:StringRes
val DecoderMode.descriptionRes: Int
    get() = when (this) {
        DecoderMode.AUTO -> R.string.decoder_mode_auto
        DecoderMode.HARDWARE -> R.string.decoder_system_description
        DecoderMode.HW_PLUS -> R.string.decoder_hardware_plus_description
        DecoderMode.FFMPEG -> R.string.decoder_app_software_description
    }
