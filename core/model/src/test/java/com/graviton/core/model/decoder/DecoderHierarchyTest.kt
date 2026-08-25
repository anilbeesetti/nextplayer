package com.graviton.core.model.decoder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Profile constants used below are the Android `MediaCodecInfo.CodecProfileLevel` values, which is
 * the numbering `CodecCapabilities.profileLevels` reports and that Graviton feeds into
 * [VideoStreamSpec.profile]:
 *
 * - `AVCProfileHigh10 = 16` (API 16)
 * - `HEVCProfileMain = 1`, `HEVCProfileMain10 = 2` (API 21)
 * - `AV1ProfileMain = 1`, `AV1ProfileMain10 = 2` (API 29)
 */
class DecoderHierarchyTest {

    private val h264High10 = VideoStreamSpec(
        codec = VideoCodec.H264,
        mimeType = "video/avc",
        profile = 16,
        bitDepth = BitDepth.TEN,
        width = 1920,
        height = 1080,
        frameRate = 30f,
    )

    private val hevcMain10 = h264High10.copy(
        codec = VideoCodec.HEVC,
        mimeType = "video/hevc",
        profile = 2,
    )

    private val av1Main10 = h264High10.copy(
        codec = VideoCodec.AV1,
        mimeType = "video/av01",
        profile = 2,
        // AV1 signals bit depth in the sequence header, so it is frequently unknown in practice.
        bitDepth = BitDepth.UNKNOWN,
    )

    private val hevc8Bit = hevcMain10.copy(profile = 1, bitDepth = BitDepth.EIGHT)

    @Test
    fun exactHardwareSupportAlwaysWinsOverAvailableSoftware() {
        val capability = DecoderCapability(
            spec = hevc8Bit,
            hardware = HardwareSupport.SUPPORTED,
            software = SoftwareSupport.AVAILABLE,
        )

        assertEquals(DecoderPath.EXACT_HARDWARE, DecoderHierarchy.resolve(capability))
    }

    @Test
    fun softwareIsOnlyTakenWhenHardwareIsDefinitivelyUnavailable() {
        val capability = DecoderCapability(
            spec = hevcMain10,
            hardware = HardwareSupport.UNSUPPORTED,
            alternativeHardware = HardwareSupport.UNSUPPORTED,
            software = SoftwareSupport.AVAILABLE,
        )

        assertEquals(DecoderPath.SOFTWARE, DecoderHierarchy.resolve(capability))
    }

    @Test
    fun unknownHardwareSupportIsAttemptedOnHardwareRatherThanForcedToSoftware() {
        val capability = DecoderCapability(
            spec = hevcMain10,
            hardware = HardwareSupport.UNKNOWN,
            software = SoftwareSupport.AVAILABLE,
        )

        assertEquals(
            "UNKNOWN must not be treated as UNSUPPORTED",
            DecoderPath.EXACT_HARDWARE,
            DecoderHierarchy.resolve(capability),
        )
    }

    @Test
    fun alternativeHardwareIsPreferredOverSoftware() {
        val capability = DecoderCapability(
            spec = hevcMain10,
            hardware = HardwareSupport.UNSUPPORTED,
            alternativeHardware = HardwareSupport.SUPPORTED,
            software = SoftwareSupport.AVAILABLE,
        )

        assertEquals(DecoderPath.ALTERNATIVE_HARDWARE, DecoderHierarchy.resolve(capability))
    }

    /**
     * This is Graviton's current AV1 10-bit situation: no hardware decoder on older SoCs, and
     * nextlib's FFmpeg build has no AV1 decoder at all, so there is nothing to fall back to.
     */
    @Test
    fun unsupportedWhenNeitherHardwareNorSoftwareCanDecode() {
        val capability = DecoderCapability(
            spec = av1Main10,
            hardware = HardwareSupport.UNSUPPORTED,
            alternativeHardware = HardwareSupport.UNSUPPORTED,
            software = SoftwareSupport.UNAVAILABLE,
        )

        assertEquals(DecoderPath.UNSUPPORTED, DecoderHierarchy.resolve(capability))
    }

    @Test
    fun unknownAlternativeHardwareStillBeatsSoftware() {
        val capability = DecoderCapability(
            spec = h264High10,
            hardware = HardwareSupport.UNSUPPORTED,
            alternativeHardware = HardwareSupport.UNKNOWN,
            software = SoftwareSupport.AVAILABLE,
        )

        assertEquals(DecoderPath.ALTERNATIVE_HARDWARE, DecoderHierarchy.resolve(capability))
    }

    @Test
    fun hardwarePathsAreNeverReportedAsSoftwarePerformanceRisks() {
        val capability = DecoderCapability(
            spec = hevcMain10.copy(width = 3840, height = 2160, frameRate = 60f),
            hardware = HardwareSupport.SUPPORTED,
            software = SoftwareSupport.AVAILABLE,
        )

        assertFalse(DecoderHierarchy.isSoftwarePerformanceRisk(capability))
    }

    @Test
    fun fourKSoftwareDecodeIsReportedAsARisk() {
        val capability = DecoderCapability(
            spec = hevcMain10.copy(width = 3840, height = 2160, frameRate = 60f),
            hardware = HardwareSupport.UNSUPPORTED,
            alternativeHardware = HardwareSupport.UNSUPPORTED,
            software = SoftwareSupport.AVAILABLE,
        )

        assertTrue(DecoderHierarchy.isSoftwarePerformanceRisk(capability))
    }

    @Test
    fun eightBit1080p30SoftwareDecodeIsNotReportedAsARisk() {
        val capability = DecoderCapability(
            spec = hevc8Bit,
            hardware = HardwareSupport.UNSUPPORTED,
            alternativeHardware = HardwareSupport.UNSUPPORTED,
            software = SoftwareSupport.AVAILABLE,
        )

        assertFalse(DecoderHierarchy.isSoftwarePerformanceRisk(capability))
    }

    @Test
    fun tenBitAt1080p60SoftwareDecodeIsReportedAsARisk() {
        val capability = DecoderCapability(
            spec = h264High10.copy(frameRate = 60f),
            hardware = HardwareSupport.UNSUPPORTED,
            alternativeHardware = HardwareSupport.UNSUPPORTED,
            software = SoftwareSupport.AVAILABLE,
        )

        assertTrue(DecoderHierarchy.isSoftwarePerformanceRisk(capability))
    }

    /** Unknown resolution must not silently pass a 10-bit software decode as safe. */
    @Test
    fun unknownResolutionFallsBackToBitDepthForTheRiskDecision() {
        val unknownSize = h264High10.copy(width = null, height = null, frameRate = null)

        assertNull(unknownSize.pixelsPerSecond)
        assertTrue(
            DecoderHierarchy.isSoftwarePerformanceRisk(
                DecoderCapability(
                    spec = unknownSize,
                    hardware = HardwareSupport.UNSUPPORTED,
                    alternativeHardware = HardwareSupport.UNSUPPORTED,
                    software = SoftwareSupport.AVAILABLE,
                ),
            ),
        )
        assertFalse(
            DecoderHierarchy.isSoftwarePerformanceRisk(
                DecoderCapability(
                    spec = unknownSize.copy(bitDepth = BitDepth.EIGHT),
                    hardware = HardwareSupport.UNSUPPORTED,
                    alternativeHardware = HardwareSupport.UNSUPPORTED,
                    software = SoftwareSupport.AVAILABLE,
                ),
            ),
        )
    }

    @Test
    fun mimeTypeAloneNeverEstablishesACodecBitDepth() {
        // Same MIME type, two different bit depths: a MIME-only check would call these equivalent.
        assertEquals(hevcMain10.mimeType, hevc8Bit.mimeType)
        assertEquals(BitDepth.TEN, hevcMain10.bitDepth)
        assertEquals(BitDepth.EIGHT, hevc8Bit.bitDepth)
    }

    @Test
    fun codecsAreResolvedFromMedia3MimeTypes() {
        assertEquals(VideoCodec.H264, VideoCodec.fromMimeType("video/avc"))
        assertEquals(VideoCodec.HEVC, VideoCodec.fromMimeType("video/hevc"))
        assertEquals(VideoCodec.AV1, VideoCodec.fromMimeType("video/av01"))
        assertEquals(VideoCodec.VP9, VideoCodec.fromMimeType("video/x-vnd.on2.vp9"))
        assertNull(VideoCodec.fromMimeType("video/dolby-vision"))
        assertNull(VideoCodec.fromMimeType(null))
    }
}
