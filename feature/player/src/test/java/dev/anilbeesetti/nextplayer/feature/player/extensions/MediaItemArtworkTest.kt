package dev.anilbeesetti.nextplayer.feature.player.extensions

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.anilbeesetti.nextplayer.feature.player.service.MAX_PUBLISHED_ARTWORK_BYTES
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaItemArtworkTest {

    @Test
    fun `artwork model prefers published data then artwork uri then media uri`() {
        val artworkUri = Uri.parse("https://example.com/art.png")
        val mediaUri = "https://example.com/video.mp4"
        val itemWithArtworkData = mediaItem(mediaUri, artworkUri, byteArrayOf(1, 2, 3))
        val itemWithArtworkUri = mediaItem(mediaUri, artworkUri)
        val itemWithoutArtwork = mediaItem(mediaUri)
        val publishedBytes = itemWithArtworkData.mediaMetadata.artworkData

        assertSame(publishedBytes, itemWithArtworkData.artworkModel)
        assertEquals(artworkUri, itemWithArtworkUri.artworkModel)
        assertEquals(mediaUri, itemWithoutArtwork.artworkModel.toString())
    }

    @Test
    fun `artwork request uri prefers metadata artwork over media id`() {
        val artworkUri = Uri.parse("https://example.com/art.png")
        val mediaUri = "https://example.com/video.mp4"

        assertEquals(artworkUri, mediaItem(mediaUri, artworkUri).artworkRequestUri)
        assertEquals(mediaUri, mediaItem(mediaUri).artworkRequestUri.toString())
    }

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

    @Test(expected = IllegalArgumentException::class)
    fun `published artwork rejects payloads above the binder safe limit`() {
        val mediaItem = MediaItem.Builder().setMediaId("video").build()

        mediaItem.withPublishedArtwork(ByteArray(MAX_PUBLISHED_ARTWORK_BYTES + 1))
    }

    private fun mediaItem(
        mediaId: String,
        artworkUri: Uri? = null,
        artworkData: ByteArray? = null,
    ): MediaItem = MediaItem.Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setArtworkUri(artworkUri)
                .apply {
                    artworkData?.let { setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
                }
                .build(),
        )
        .build()
}
