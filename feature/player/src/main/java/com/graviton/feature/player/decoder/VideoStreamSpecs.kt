package com.graviton.feature.player.decoder

import android.media.MediaCodecInfo
import android.annotation.SuppressLint
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.common.Format
import com.graviton.core.model.decoder.BitDepth
import com.graviton.core.model.decoder.VideoCodec
import com.graviton.core.model.decoder.VideoStreamSpec

/**
 * Builds the [VideoStreamSpec] a capability check needs out of a Media3 [Format].
 *
 * [Format.profile] and [Format.level] are taken at face value and compared against
 * `MediaCodecInfo.CodecProfileLevel`, which is the numbering [DeviceDecoderCapabilities] reads back
 * out of `CodecCapabilities.profileLevels`. Both sides therefore have to agree on that numbering for
 * a profile comparison to mean anything; if a profile ever looks wrong in the diagnostics log, this
 * mapping is the first place to look.
 */
@SuppressLint("UnsafeOptInUsageError")
fun Format.toVideoStreamSpec(): VideoStreamSpec? {
    val codec = VideoCodec.fromMimeType(sampleMimeType) ?: return null
    val profileLevel = MediaCodecUtil.getCodecProfileAndLevel(this)
    val fmtProfile = profileLevel?.first ?: Format.NO_VALUE
    val fmtLevel = profileLevel?.second ?: Format.NO_VALUE
    val fmtWidth = this.width
    val fmtHeight = this.height
    val fmtFrameRate = this.frameRate

    return VideoStreamSpec(
        codec = codec,
        mimeType = sampleMimeType ?: codec.mimeType,
        profile = if (fmtProfile == Format.NO_VALUE) null else fmtProfile,
        level = if (fmtLevel == Format.NO_VALUE) null else fmtLevel,
        width = if (fmtWidth == Format.NO_VALUE) null else fmtWidth,
        height = if (fmtHeight == Format.NO_VALUE) null else fmtHeight,
        frameRate = if (fmtFrameRate == Format.NO_VALUE.toFloat() || fmtFrameRate <= 0f) null else fmtFrameRate,
        bitDepth = if (fmtProfile == Format.NO_VALUE) BitDepth.UNKNOWN else bitDepthFor(codec, fmtProfile),
    )
}

/**
 * Derives the sample bit depth from a codec profile.
 *
 * Only profiles whose bit depth is unambiguous are mapped. Everything else, including AV1 Main,
 * returns [BitDepth.UNKNOWN]: AV1 Main covers both 8-bit and 10-bit and the real answer lives in the
 * sequence header, which neither Media3's [Format] nor nextlib's mediainfo exposes.
 *
 * Extractors frequently leave [Format.profile] unset as well, so [BitDepth.UNKNOWN] is the common
 * case and callers must not read it as "8-bit".
 */
private fun bitDepthFor(codec: VideoCodec, profile: Int): BitDepth = when (codec) {
    VideoCodec.H264 -> when (profile) {
        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10 -> BitDepth.TEN

        MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline,
        MediaCodecInfo.CodecProfileLevel.AVCProfileMain,
        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
        -> BitDepth.EIGHT

        else -> BitDepth.UNKNOWN
    }

    VideoCodec.HEVC -> when (profile) {
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10,
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10,
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus,
        -> BitDepth.TEN

        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain -> BitDepth.EIGHT

        else -> BitDepth.UNKNOWN
    }

    VideoCodec.AV1 -> when (profile) {
        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10,
        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10,
        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10Plus,
        -> BitDepth.TEN

        // AV1 Main is 8- or 10-bit depending on the sequence header, so it proves nothing.
        else -> BitDepth.UNKNOWN
    }

    VideoCodec.VP8, VideoCodec.VP9 -> BitDepth.UNKNOWN
}
