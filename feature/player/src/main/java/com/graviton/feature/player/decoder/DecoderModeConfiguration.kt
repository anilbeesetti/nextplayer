package com.graviton.feature.player.decoder

import androidx.media3.exoplayer.DefaultRenderersFactory
import com.graviton.core.model.DecoderMode

/**
 * The two Media3 knobs that actually decide which decoder plays a video.
 *
 * @param extensionRendererMode where nextlib's FFmpeg renderers sit relative to MediaCodec's.
 * @param enableDecoderFallback whether `MediaCodecRenderer` retries another decoder when the first
 *   one fails to initialise or cannot handle the format.
 */
data class DecoderModeConfiguration(
    val extensionRendererMode: Int,
    val enableDecoderFallback: Boolean,
)

/**
 * Maps the user-facing [DecoderMode] onto Media3's decoder-selection knobs.
 *
 * Fallback is enabled for every mode. It used to be enabled only for [DecoderMode.AUTO], which
 * turned a recoverable decoder failure in HW, HW+ and SW into a hard playback error with no retry.
 * Enabling it everywhere is safe: fallback is a retry path, not a preference, so it can never make
 * Media3 pick a software decoder over a hardware decoder that initialised successfully.
 *
 * Note on [DecoderMode.HARDWARE_PLUS]: upstream Media3 only exposes three distinct behaviours
 * (`EXTENSION_RENDERER_MODE_OFF` / `_ON` / `_PREFER`). However, users expect a strict difference.
 * For HARDWARE_PLUS we keep `EXTENSION_RENDERER_MODE_ON` but without enableDecoderFallback, forcing it
 * to fail if hardware isn't working properly without silently falling back to software. For AUTO, fallback is enabled.
 * For HARDWARE, we completely turn off extensions.
 * For SOFTWARE, we prefer extensions.
 */
fun DecoderMode.toConfiguration(): DecoderModeConfiguration = DecoderModeConfiguration(
    extensionRendererMode = when (this) {
        DecoderMode.AUTO -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
        DecoderMode.HARDWARE -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
        DecoderMode.HARDWARE_PLUS -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
        DecoderMode.SOFTWARE -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
    },
    enableDecoderFallback = true,
)

/** Short label used in the diagnostics log, matching the in-player chip. */
fun DecoderMode.label(): String = when (this) {
    DecoderMode.AUTO -> "Auto"
    DecoderMode.HARDWARE -> "HW"
    DecoderMode.HARDWARE_PLUS -> "HW+"
    DecoderMode.SOFTWARE -> "SW"
}
