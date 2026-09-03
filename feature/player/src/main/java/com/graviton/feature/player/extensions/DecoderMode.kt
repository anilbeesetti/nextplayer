package com.graviton.feature.player.extensions

import androidx.annotation.StringRes
import com.graviton.core.model.DecoderMode
import com.graviton.core.ui.R

/** Full label for a decoder mode, used wherever there is room for one. */
@StringRes
fun DecoderMode.nameRes(): Int = when (this) {
    DecoderMode.AUTO -> R.string.decoder_mode_auto
    DecoderMode.HARDWARE -> R.string.decoder_mode_hardware
    DecoderMode.HARDWARE_PLUS -> R.string.decoder_mode_hardware_plus
    DecoderMode.SOFTWARE -> R.string.decoder_mode_software
}

/** One line explaining what the mode actually does. */
@StringRes
fun DecoderMode.descriptionRes(): Int = when (this) {
    DecoderMode.AUTO -> R.string.decoder_mode_auto_desc
    DecoderMode.HARDWARE -> R.string.decoder_mode_hardware_desc
    DecoderMode.HARDWARE_PLUS -> R.string.decoder_mode_hardware_plus_desc
    DecoderMode.SOFTWARE -> R.string.decoder_mode_software_desc
}
