package dev.anilbeesetti.nextplayer.feature.player.service

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import dev.anilbeesetti.nextplayer.feature.player.extensions.copy
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ApiMediaSourceFactoryTest {
    @Test
    fun compatibilityExtractionOnlyAppliesToClearProgressiveMedia() {
        val progressive = MediaItem.fromUri("https://example.test/video.mp4")
            .copy(videoDecoderMode = DecoderMode.HW_PLUS)
        assertTrue(progressive.usesFfmpegExtractor)
        for (uri in listOf(
            "https://example.test/playlist.m3u8",
            "https://example.test/manifest.mpd",
            "rtsp://example.test/video",
        )) {
            assertFalse(
                MediaItem.fromUri(
                    uri,
                ).copy(videoDecoderMode = DecoderMode.HW_PLUS).usesFfmpegExtractor,
            )
        }
        val drmProtected = progressive.buildUpon().setDrmConfiguration(
            MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                .setLicenseUri("https://example.test/license").build(),
        ).build()
        assertFalse(drmProtected.usesFfmpegExtractor)
    }
}
