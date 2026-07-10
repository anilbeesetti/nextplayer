package dev.anilbeesetti.nextplayer.feature.player.model

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
