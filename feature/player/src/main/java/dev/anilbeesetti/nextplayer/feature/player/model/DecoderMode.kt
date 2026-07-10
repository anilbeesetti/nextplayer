package dev.anilbeesetti.nextplayer.feature.player.model

import dev.anilbeesetti.nextplayer.core.model.DecoderPriority

/** Video decoder modes shown in the player controls. */
enum class DecoderMode(val label: String) {
    HW_PLUS("HW+"),
    HW("HW"),
    SW("SW"),
    ;

    companion object {
        fun from(value: String?): DecoderMode? = entries.find { it.name == value }
    }
}

fun DecoderPriority.toDecoderMode(): DecoderMode {
    return when (this) {
        DecoderPriority.PREFER_DEVICE -> DecoderMode.HW_PLUS
        DecoderPriority.DEVICE_ONLY -> DecoderMode.HW
        DecoderPriority.PREFER_APP -> DecoderMode.SW
    }
}
