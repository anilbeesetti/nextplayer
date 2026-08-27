package dev.anilbeesetti.nextplayer.feature.player.model

import androidx.annotation.StringRes
import dev.anilbeesetti.nextplayer.core.ui.R
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode

val selectableDecoderModes = listOf(
    DecoderMode.HARDWARE,
    DecoderMode.SOFTWARE,
    DecoderMode.APP_SOFTWARE,
)

@get:StringRes
val DecoderMode.labelRes: Int
    get() = when (this) {
        DecoderMode.HARDWARE -> R.string.decoder_mode_hardware
        DecoderMode.SOFTWARE -> R.string.decoder_mode_system_software
        DecoderMode.APP_SOFTWARE -> R.string.decoder_mode_app_software
    }

@get:StringRes
val DecoderMode.descriptionRes: Int
    get() = when (this) {
        DecoderMode.HARDWARE -> R.string.decoder_hardware_description
        DecoderMode.SOFTWARE -> R.string.decoder_system_software_description
        DecoderMode.APP_SOFTWARE -> R.string.decoder_app_software_description
    }
