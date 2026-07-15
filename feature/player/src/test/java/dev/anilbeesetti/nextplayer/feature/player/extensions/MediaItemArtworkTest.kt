package dev.anilbeesetti.nextplayer.feature.player.extensions

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaItemArtworkTest {

    @Test
    fun `published artwork uses in-process data without exposing a private uri`() {
        val artworkData = byteArrayOf(1, 2, 3, 4)
        val mediaItem = MediaItem.Builder()
            .setMediaId("video")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Video")
                    .build(),
            )
            .build()

        val result = mediaItem.withPublishedArtwork(artworkData)

        assertArrayEquals(artworkData, result.mediaMetadata.artworkData)
        assertEquals(MediaMetadata.PICTURE_TYPE_FRONT_COVER, result.mediaMetadata.artworkDataType)
        assertNull(result.mediaMetadata.artworkUri)
        assertEquals("Video", result.mediaMetadata.title)
    }
}
