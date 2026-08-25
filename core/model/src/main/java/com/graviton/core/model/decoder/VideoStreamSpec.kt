package com.graviton.core.model.decoder

/**
 * Video codecs Graviton reasons about when choosing a decoder.
 *
 * [mimeType] holds the Media3 `MimeTypes` string. Matching on MIME type alone is NOT enough to
 * decide whether a device can play a stream: `video/hevc` says nothing about HEVC Main 10, and
 * `video/avc` says nothing about H.264 High 10. Profile, level and bit depth are carried alongside
 * in [VideoStreamSpec] for exactly that reason.
 */
enum class VideoCodec(val mimeType: String) {
    H264("video/avc"),
    HEVC("video/hevc"),
    AV1("video/av01"),
    VP8("video/x-vnd.on2.vp8"),
    VP9("video/x-vnd.on2.vp9"),
    ;

    companion object {
        /** Returns the codec for [mimeType], or null when Graviton has no capability model for it. */
        fun fromMimeType(mimeType: String?): VideoCodec? = entries.firstOrNull { it.mimeType == mimeType }
    }
}

/**
 * Sample bit depth of a video stream.
 *
 * Bit depth cannot be read off the MIME type, and it is not always readable off the profile either:
 * AV1 Main covers both 8-bit and 10-bit, and the bit depth actually lives in the AV1 sequence
 * header. Neither Media3's `Format` nor nextlib's mediainfo exposes it, so [UNKNOWN] is a normal
 * outcome rather than an edge case. Callers must treat [UNKNOWN] as "do not assume 8-bit".
 */
enum class BitDepth {
    EIGHT,
    TEN,
    UNKNOWN,
}

/**
 * The parts of a video track that determine whether one specific decoder can play it.
 *
 * A device advertising `video/hevc` does not automatically support every HEVC Main 10 stream, so a
 * capability check has to consider [profile], [level], [bitDepth] and the [width]x[height] /
 * [frameRate] envelope rather than the MIME type.
 *
 * [profile] and [level] use the Android `MediaCodecInfo.CodecProfileLevel` numbering, which is the
 * same numbering `MediaCodecInfo.CodecCapabilities.profileLevels` reports. Null means the container
 * or the demuxer did not carry the value.
 */
data class VideoStreamSpec(
    val codec: VideoCodec,
    val mimeType: String,
    val profile: Int? = null,
    val level: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Float? = null,
    val bitDepth: BitDepth = BitDepth.UNKNOWN,
) {
    /**
     * Decoded pixels per second, or null when resolution or frame rate is unknown.
     *
     * Used as a coarse proxy for software-decoding cost so that a capability report can warn before
     * a phone is asked to software-decode 4K60.
     */
    val pixelsPerSecond: Long?
        get() {
            val w = width ?: return null
            val h = height ?: return null
            val fps = frameRate ?: return null
            if (w <= 0 || h <= 0 || fps <= 0f) return null
            return (w.toLong() * h.toLong() * fps).toLong()
        }

    /** Human-readable summary used by the playback diagnostics log. */
    fun describe(): String = buildString {
        append(codec.name)
        append(" ")
        append(bitDepth.name.lowercase())
        append("-bit")
        profile?.let { append(" profile=").append(it) }
        level?.let { append(" level=").append(it) }
        if (width != null && height != null) append(" ${width}x$height")
        frameRate?.let { append(" @").append(it).append("fps") }
    }
}
