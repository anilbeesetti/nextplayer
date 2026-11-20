package dev.anilbeesetti.nextplayer.feature.player.service

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.source.UnrecognizedInputFormatException
import dev.anilbeesetti.nextplayer.feature.player.extensions.copy
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ApiMediaSourceFactoryTest {
    @Test
    fun sidecarParserFailureDoesNotMatchThePrimarySource() {
        val video = Uri.parse("https://example.test/video.mp4")
        val audio = Uri.parse("https://example.test/external.mka")
        val item = MediaItem.fromUri(video)
        assertFalse(
            item.hasPrimaryParserError(
                UnrecognizedInputFormatException(
                    "unsupported",
                    audio,
                ),
            ),
        )
        assertTrue(
            item.hasPrimaryParserError(
                UnrecognizedInputFormatException(
                    "unsupported",
                    video,
                ),
            ),
        )
    }

    @Test
    fun softwareCanDecodeWithTheStandardReaderAfterFfmpegReaderFailure() {
        val item = MediaItem.fromUri("https://example.test/video.mp4")
            .withVideoDecoder(
                DecoderMode.FFMPEG,
                useFfmpegForSoftware = false,
            )
        assertEquals(
            DecoderMode.FFMPEG,
            item.videoDecoderBackend,
        )
        assertFalse(item.usesFfmpegExtractor)
    }

    @Test
    fun automaticModeUsesTheBackendOfTheCurrentReader() {
        val item = MediaItem.fromUri("https://example.test/video.mp4")
        assertEquals(
            DecoderMode.HARDWARE,
            item.videoDecoderBackend,
        )
        assertEquals(
            DecoderMode.HW_PLUS,
            item.copy(ffmpegExtraction = true).videoDecoderBackend,
        )
    }

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
