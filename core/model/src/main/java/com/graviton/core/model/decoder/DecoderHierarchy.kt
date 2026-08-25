package com.graviton.core.model.decoder

/**
 * The decoder Graviton should attempt for a stream, in the order it should be attempted.
 *
 * ```
 * EXACT_HARDWARE -> ALTERNATIVE_HARDWARE -> SOFTWARE -> UNSUPPORTED
 * ```
 */
enum class DecoderPath {
    /** The device's hardware decoder advertises the exact profile/level the stream needs. */
    EXACT_HARDWARE,

    /** A different hardware decoder can take the stream even though the first one cannot. */
    ALTERNATIVE_HARDWARE,

    /** No usable hardware decoder; an app-bundled software decoder has to do the work. */
    SOFTWARE,

    /** No hardware and no software decoder. Playback should fail with a clear message. */
    UNSUPPORTED,
}

/**
 * Resolves a [DecoderCapability] into the [DecoderPath] Graviton should take.
 *
 * Hardware is always preferred. Software is a fallback, never a preference, and unknown capability
 * resolves optimistically towards hardware: it is far cheaper to let Media3 attempt the hardware
 * decoder and fall back on failure than it is to abandon a hardware path that would have worked.
 */
object DecoderHierarchy {

    /** 4K30. Above this, software decode on a phone is expected to drop frames. */
    private const val HIGH_LOAD_PIXELS_PER_SECOND = 3840L * 2160L * 30L

    /** 1080p60. 10-bit decode costs noticeably more than 8-bit at the same pixel rate. */
    private const val TEN_BIT_LOAD_PIXELS_PER_SECOND = 1920L * 1080L * 60L

    fun resolve(capability: DecoderCapability): DecoderPath = with(capability) {
        when {
            hardware == HardwareSupport.SUPPORTED -> DecoderPath.EXACT_HARDWARE

            // Unknown is not a refusal. Attempt hardware and let Media3's decoder fallback handle
            // an initialisation failure; forcing software here would discard a working hardware path.
            hardware == HardwareSupport.UNKNOWN -> DecoderPath.EXACT_HARDWARE

            alternativeHardware == HardwareSupport.SUPPORTED -> DecoderPath.ALTERNATIVE_HARDWARE
            alternativeHardware == HardwareSupport.UNKNOWN -> DecoderPath.ALTERNATIVE_HARDWARE

            software == SoftwareSupport.AVAILABLE -> DecoderPath.SOFTWARE

            else -> DecoderPath.UNSUPPORTED
        }
    }

    /**
     * Whether routing [capability] to a software decoder is likely to drop frames.
     *
     * Only meaningful when [resolve] returns [DecoderPath.SOFTWARE]; it returns false for every
     * hardware path because a hardware decoder's throughput is not a Graviton concern.
     *
     * When resolution or frame rate is unknown the decision falls back to bit depth alone, since
     * 10-bit is the case that most reliably overruns a mobile CPU.
     */
    fun isSoftwarePerformanceRisk(capability: DecoderCapability): Boolean {
        if (resolve(capability) != DecoderPath.SOFTWARE) return false
        val load = capability.spec.pixelsPerSecond
            ?: return capability.spec.bitDepth == BitDepth.TEN
        if (load >= HIGH_LOAD_PIXELS_PER_SECOND) return true
        return capability.spec.bitDepth == BitDepth.TEN && load >= TEN_BIT_LOAD_PIXELS_PER_SECOND
    }
}
