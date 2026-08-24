package dev.anilbeesetti.nextplayer.feature.player.extensions

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
}
