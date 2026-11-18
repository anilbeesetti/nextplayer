package dev.anilbeesetti.nextplayer.feature.player.extensions

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaItemTest {
    @Test
    fun switchingToSoftwareRetainsCompatibilityExtraction() {
        val item = androidx.media3.common.MediaItem.fromUri("file:///video.mp4")
            .copy(
                videoDecoderMode = DecoderMode.HW_PLUS,
                ffmpegExtraction = true,
            )
        val software = item.copy(videoDecoderMode = DecoderMode.FFMPEG)
        org.junit.Assert.assertTrue(software.mediaMetadata.ffmpegExtraction)
        org.junit.Assert.assertEquals(
            DecoderMode.FFMPEG,
            software.mediaMetadata.videoDecoderMode,
        )
        org.junit.Assert.assertFalse(
            software.copy(
                videoDecoderMode = DecoderMode.HARDWARE,
                ffmpegExtraction = false,
            ).mediaMetadata.ffmpegExtraction,
        )
    }

    @Test
    fun `copy creates metadata extras when source extras are null`() {
        val mediaItem = MediaItem.Builder()
            .setMediaId("https://example.com/stream.mp4")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Remote stream")
                    .build(),
            )
            .build()

        val copiedItem = mediaItem.copy(audioTrackIndex = 2)

        assertNotNull(copiedItem.mediaMetadata.extras)
        assertEquals(
            2,
            copiedItem.mediaMetadata.audioTrackIndex,
        )
        assertEquals(
            "Remote stream",
            copiedItem.mediaMetadata.title,
        )
    }

    @Test
    fun decoderChoicesSurviveMetadataCopiesAndBundleRoundTrip() {
        for (videoMode in DecoderMode.entries) {
            for (audioMode in DecoderMode.entries) {
                val item = MediaItem.Builder().build()
                    .copy(
                        videoDecoderMode = videoMode,
                        audioDecoderMode = audioMode,
                    )
                    .copy(
                        positionMs = 12_000,
                        subtitleTrackIndex = 2,
                    )
                val restored = MediaItem.fromBundle(item.toBundle())
                assertEquals(
                    videoMode,
                    restored.mediaMetadata.videoDecoderMode,
                )
                assertEquals(
                    audioMode,
                    restored.mediaMetadata.audioDecoderMode,
                )
                val fallback = restored.copy(audioDecoderMode = DecoderMode.HW_PLUS)
                assertEquals(
                    videoMode,
                    fallback.mediaMetadata.videoDecoderMode,
                )
                assertEquals(
                    DecoderMode.HW_PLUS,
                    fallback.mediaMetadata.audioDecoderMode,
                )
            }
        }
    }

    @Test
    fun newItemsHaveNoDecoderChoiceAndInvalidNamesAreIgnored() {
        assertNull(MediaItem.EMPTY.mediaMetadata.videoDecoderMode)
        assertNull(MediaItem.EMPTY.mediaMetadata.audioDecoderMode)
        val metadata = MediaMetadata.Builder()
            .setExtras(
                videoDecoderMode = DecoderMode.AUTO,
                audioDecoderMode = DecoderMode.HARDWARE,
            )
            .build()
        assertEquals(
            DecoderMode.AUTO,
            metadata.videoDecoderMode,
        )
        assertEquals(
            DecoderMode.HARDWARE,
            metadata.audioDecoderMode,
        )
        metadata.extras!!.keySet().forEach {
            metadata.extras!!.putString(
                it,
                "unknown",
            )
        }
        assertNull(metadata.videoDecoderMode)
        assertNull(metadata.audioDecoderMode)
    }
}
