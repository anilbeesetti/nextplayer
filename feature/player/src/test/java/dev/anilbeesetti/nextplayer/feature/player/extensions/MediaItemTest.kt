package dev.anilbeesetti.nextplayer.feature.player.extensions

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaItemTest {
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
        assertEquals(2, copiedItem.mediaMetadata.audioTrackIndex)
        assertEquals("Remote stream", copiedItem.mediaMetadata.title)
    }

    @Test
    fun decoderChoicesSurviveMetadataCopiesAndBundleRoundTrip() {
        for (videoMode in DecoderMode.entries) {
            for (audioMode in DecoderMode.entries) {
                val item = MediaItem.Builder().build()
                    .copy(videoDecoderMode = videoMode, audioDecoderMode = audioMode)
                    .copy(positionMs = 12_000, subtitleTrackIndex = 2)
                val restored = MediaItem.fromBundle(item.toBundle())
                assertEquals(videoMode, restored.mediaMetadata.videoDecoderMode)
                assertEquals(audioMode, restored.mediaMetadata.audioDecoderMode)
                val fallback = restored.copy(audioDecoderMode = DecoderMode.SOFTWARE)
                assertEquals(videoMode, fallback.mediaMetadata.videoDecoderMode)
                assertEquals(DecoderMode.SOFTWARE, fallback.mediaMetadata.audioDecoderMode)
            }
        }
    }

    @Test
    fun newItemsHaveNoDecoderChoiceAndInvalidNamesAreIgnored() {
        assertNull(MediaItem.EMPTY.mediaMetadata.videoDecoderMode)
        assertNull(MediaItem.EMPTY.mediaMetadata.audioDecoderMode)
        val metadata = MediaMetadata.Builder()
            .setExtras(videoDecoderMode = DecoderMode.AUTO, audioDecoderMode = DecoderMode.HARDWARE)
            .build()
        assertEquals(DecoderMode.AUTO, metadata.videoDecoderMode)
        assertEquals(DecoderMode.HARDWARE, metadata.audioDecoderMode)
        metadata.extras!!.keySet().forEach { metadata.extras!!.putString(it, "unknown") }
        assertNull(metadata.videoDecoderMode)
        assertNull(metadata.audioDecoderMode)
    }

}
