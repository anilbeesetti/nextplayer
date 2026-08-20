package dev.anilbeesetti.nextplayer.settings.extensions

import androidx.compose.runtime.Composable
import dev.anilbeesetti.nextplayer.core.model.DecoderMode

@Composable
fun DecoderMode.name(): String {
    return when (this) {
        DecoderMode.AUTO -> "Auto"
        DecoderMode.HARDWARE -> "HW"
        DecoderMode.HARDWARE_PLUS -> "HW+"
        DecoderMode.SOFTWARE -> "SW"
    }
}
