package com.graviton.feature.player.decoder

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import com.graviton.core.model.decoder.BitDepth
import com.graviton.core.model.decoder.HardwareSupport
import com.graviton.core.model.decoder.VideoCodec
import com.graviton.core.model.decoder.VideoStreamSpec
import kotlin.math.roundToInt

/**
 * Answers "can this device hardware-decode this exact stream?" by asking the device.
 *
 * Everything here is derived from `MediaCodecList` at runtime. There are no Snapdragon / MediaTek /
 * Exynos allow-lists: the codec name prefixes below exist only to separate software codecs from
 * hardware ones on API levels older than 29, where `MediaCodecInfo.isHardwareAccelerated` is absent.
 *
 * Two platform realities shape the results and are why [HardwareSupport.UNKNOWN] exists:
 *  - `CodecCapabilities.profileLevels` is empty on a number of vendor codecs;
 *  - `CodecCapabilities.isFormatSupported` is a hint, and some devices answer it incorrectly.
 *
 * Callers must therefore treat [HardwareSupport.UNKNOWN] as "try hardware first", never as "no
 * hardware". A wrong "no" from `isFormatSupported` is also recoverable, because decoder fallback
 * stays enabled and Media3 will retry the next decoder before giving up.
 */
class DeviceDecoderCapabilities {

    private val decoders: List<MediaCodecInfo> by lazy {
        runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.toList() }
            .getOrDefault(emptyList())
            .filter { !it.isEncoder }
    }

    private val capabilitiesByDecoderAndMime =
        HashMap<Pair<String, String>, MediaCodecInfo.CodecCapabilities?>()

    /** Every decoder name this device exposes for [mimeType], hardware decoders first. */
    fun decoderNames(mimeType: String): List<String> =
        decoders.filter { capabilitiesFor(it, mimeType) != null }
            .sortedByDescending { isHardwareDecoder(it) }
            .map { it.name }

    /**
     * The device's verdict on hardware decoding [spec], considering codec, profile, level, bit depth
     * and the resolution / frame-rate envelope.
     *
     * This scans every hardware decoder for the codec, so a [HardwareSupport.SUPPORTED] result means
     * "at least one hardware decoder on this device can take this exact stream".
     */
    fun hardwareSupportFor(spec: VideoStreamSpec): HardwareSupport {
        // Could not enumerate codecs at all, so nothing can be concluded.
        if (decoders.isEmpty()) return HardwareSupport.UNKNOWN

        val hardwareCapabilities = decoders
            .filter { isHardwareDecoder(it) }
            .mapNotNull { capabilitiesFor(it, spec.mimeType) }

        // No hardware decoder advertises this codec at all. A definite answer.
        if (hardwareCapabilities.isEmpty()) return HardwareSupport.UNSUPPORTED

        // Every hardware decoder for this codec was positively identified as the wrong bit depth.
        if (hardwareCapabilities.none { it.matchesBitDepth(spec) }) return HardwareSupport.UNSUPPORTED

        val format = spec.toMediaFormat() ?: return HardwareSupport.UNKNOWN
        return if (hardwareCapabilities.any { it.isFormatSupportedSafely(format) }) {
            HardwareSupport.SUPPORTED
        } else {
            HardwareSupport.UNSUPPORTED
        }
    }

    /**
     * Whether Media3's decoder fallback actually has a second hardware decoder to fall back to.
     *
     * Returns [HardwareSupport.SUPPORTED] only when two or more hardware decoders can handle [spec],
     * [HardwareSupport.UNSUPPORTED] when there is a single candidate or none, and
     * [HardwareSupport.UNKNOWN] when the envelope could not be checked.
     */
    fun alternativeHardwareSupportFor(spec: VideoStreamSpec): HardwareSupport {
        if (decoders.isEmpty()) return HardwareSupport.UNKNOWN
        val candidates = decoders
            .filter { isHardwareDecoder(it) }
            .mapNotNull { capabilitiesFor(it, spec.mimeType) }
            .filter { it.matchesBitDepth(spec) }
        if (candidates.size < 2) return HardwareSupport.UNSUPPORTED

        val format = spec.toMediaFormat() ?: return HardwareSupport.UNKNOWN
        return if (candidates.count { it.isFormatSupportedSafely(format) } >= 2) {
            HardwareSupport.SUPPORTED
        } else {
            HardwareSupport.UNSUPPORTED
        }
    }

    /**
     * Whether [decoderName] names a hardware decoder on this device.
     *
     * Null means the name is not a platform `MediaCodec` at all, which is how an app-bundled decoder
     * such as nextlib's FFmpeg renderer presents itself.
     */
    fun isHardwareDecoderName(decoderName: String): Boolean? =
        decoders.firstOrNull { it.name == decoderName }?.let { isHardwareDecoder(it) }

    private fun capabilitiesFor(
        info: MediaCodecInfo,
        mimeType: String,
    ): MediaCodecInfo.CodecCapabilities? {
        val key = info.name to mimeType
        if (capabilitiesByDecoderAndMime.containsKey(key)) return capabilitiesByDecoderAndMime[key]
        val capabilities = runCatching { info.getCapabilitiesForType(mimeType) }.getOrNull()
        capabilitiesByDecoderAndMime[key] = capabilities
        return capabilities
    }

    private fun isHardwareDecoder(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isHardwareAccelerated
        } else {
            !isSoftwareCodecName(info.name)
        }

    /**
     * A decoder is only 10-bit capable if it advertises a 10-bit profile.
     *
     * Returns true when the bit depth cannot be cross-checked - either because the stream's bit
     * depth is unknown, or because the device advertises no profiles at all. Failing open here is
     * deliberate: the alternative is abandoning a hardware path that would have worked.
     */
    private fun MediaCodecInfo.CodecCapabilities.matchesBitDepth(spec: VideoStreamSpec): Boolean {
        if (spec.bitDepth != BitDepth.TEN) return true

        val profiles = profileLevels?.map { it.profile }?.toSet()
        if (profiles.isNullOrEmpty()) return true

        val tenBitProfiles = tenBitProfilesFor(spec.codec) ?: return true
        return profiles.any { it in tenBitProfiles }
    }

    /**
     * 10-bit profiles per codec, using the values `MediaCodecInfo.CodecProfileLevel` defines.
     *
     * Null means "Graviton has no 10-bit profile list for this codec", in which case no profile
     * filtering is applied. H.264 High 4:2:2 and High 4:4:4 also carry 10-bit samples but are
     * intentionally omitted; an unrecognised profile fails open towards hardware.
     */
    private fun tenBitProfilesFor(codec: VideoCodec): Set<Int>? = when (codec) {
        VideoCodec.H264 -> setOf(MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10)
        VideoCodec.HEVC -> setOf(
            MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10,
            MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10,
            MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus,
        )
        VideoCodec.AV1 -> setOf(
            MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10,
            MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10,
            MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10Plus,
        )
        VideoCodec.VP8, VideoCodec.VP9 -> null
    }

    private fun MediaCodecInfo.CodecCapabilities.isFormatSupportedSafely(
        format: MediaFormat,
    ): Boolean = runCatching { isFormatSupported(format) }.getOrDefault(false)

    private fun VideoStreamSpec.toMediaFormat(): MediaFormat? {
        val w = width ?: return null
        val h = height ?: return null
        if (w <= 0 || h <= 0) return null
        return MediaFormat().apply {
            setString(MediaFormat.KEY_MIME, mimeType)
            setInteger(MediaFormat.KEY_WIDTH, w)
            setInteger(MediaFormat.KEY_HEIGHT, h)
            frameRate?.takeIf { it > 0f }?.let { setInteger(MediaFormat.KEY_FRAME_RATE, it.roundToInt()) }
            profile?.let { setInteger(MediaFormat.KEY_PROFILE, it) }
            level?.let { setInteger(MediaFormat.KEY_LEVEL, it) }
        }
    }

    private companion object {
        val SOFTWARE_CODEC_NAME_PREFIXES = listOf(
            "omx.google.",
            "c2.android.",
            "c2.google.",
        )

        fun isSoftwareCodecName(name: String): Boolean {
            val lowerCase = name.lowercase()
            return SOFTWARE_CODEC_NAME_PREFIXES.any { lowerCase.startsWith(it) }
        }
    }
}
