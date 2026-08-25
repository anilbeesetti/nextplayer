package com.graviton.core.model.decoder

/** Whether a hardware decoder can handle one exact stream specification. */
enum class HardwareSupport {
    /** A hardware decoder advertises the exact profile/level and envelope the stream needs. */
    SUPPORTED,

    /** The device has hardware decoders for this codec, but none advertise what the stream needs. */
    UNSUPPORTED,

    /**
     * Capability could not be determined.
     *
     * This is common rather than exceptional: `CodecCapabilities.profileLevels` is empty on a
     * number of vendor codecs, and `CodecCapabilities.isFormatSupported` is documented as a hint
     * that some devices answer incorrectly. [UNKNOWN] must never be collapsed into [UNSUPPORTED] -
     * doing so would push a stream onto a software decoder when the hardware path might have worked.
     */
    UNKNOWN,
}

/** Whether an in-process software decoder exists for the codec. */
enum class SoftwareSupport {
    AVAILABLE,
    UNAVAILABLE,
}

/**
 * The answer to "can this device play this exact stream, and how should Graviton try?"
 *
 * [hardware] is the exact-match verdict, [alternativeHardware] is the verdict for a second hardware
 * decoder for the same codec (a different vendor block, or the same block at a lower profile), and
 * [software] is whether an app-bundled decoder can take over when neither hardware path applies.
 */
data class DecoderCapability(
    val spec: VideoStreamSpec,
    val hardware: HardwareSupport,
    val alternativeHardware: HardwareSupport = HardwareSupport.UNKNOWN,
    val software: SoftwareSupport,
) {
    /** Human-readable summary used by the playback diagnostics log. */
    fun describe(): String =
        "${spec.describe()} hw=$hardware altHw=$alternativeHardware sw=$software"
}
