package dev.anilbeesetti.nextplayer.feature.player.model

import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode as NextDecoderMode

/** Extraction and decoder preferences presented by Next Player. */
enum class DecoderMode {
    AUTO,
    HARDWARE,
    HW_PLUS,
    FFMPEG,
    ;

    val systemDecoderMode: NextDecoderMode
        get() = when (this) {
            AUTO -> NextDecoderMode.AUTO
            HARDWARE, HW_PLUS -> NextDecoderMode.SYSTEM
            FFMPEG -> NextDecoderMode.FFMPEG
        }
}

internal fun NextDecoderMode.playerMode(): DecoderMode = when (this) {
    NextDecoderMode.AUTO -> DecoderMode.AUTO
    NextDecoderMode.FFMPEG -> DecoderMode.FFMPEG
    else -> DecoderMode.HARDWARE
}
