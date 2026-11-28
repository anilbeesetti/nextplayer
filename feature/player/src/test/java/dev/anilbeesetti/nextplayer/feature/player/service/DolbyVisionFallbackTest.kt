package dev.anilbeesetti.nextplayer.feature.player.service

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DolbyVisionFallbackTest {
    @Test
    fun hevcFallbackRequiresACompatibleBaseLayer() {
        val limitedRangePq = ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT2020)
            .setColorRange(C.COLOR_RANGE_LIMITED)
            .setColorTransfer(C.COLOR_TRANSFER_ST2084)
            .build()
        val profile7 = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
            .setCodecs("dvhe.07.06")
            .setColorInfo(limitedRangePq)
            .build()
        assertEquals(
            MimeTypes.VIDEO_H265,
            MediaCodecUtil.getAlternativeCodecMimeType(profile7),
        )

        val fullRangePq = limitedRangePq.buildUpon().setColorRange(C.COLOR_RANGE_FULL).build()
        val incompatibleProfile7 = profile7.buildUpon().setColorInfo(fullRangePq).build()
        assertNull(MediaCodecUtil.getAlternativeCodecMimeType(incompatibleProfile7))

        val profile5 = profile7.buildUpon().setCodecs("dvhe.05.06").build()
        assertNull(MediaCodecUtil.getAlternativeCodecMimeType(profile5))
    }
}
