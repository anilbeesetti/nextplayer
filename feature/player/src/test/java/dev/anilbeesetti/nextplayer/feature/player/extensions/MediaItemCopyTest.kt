package dev.anilbeesetti.nextplayer.feature.player.extensions

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaItemCopyTest {

    @Test
    fun `copy creates extras when source metadata has none`() {
        val item = MediaItem.Builder()
            .setMediaId("https://stream.example/video.m3u8")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Channel").build())
            .build()

        val copied = item.copy(durationMs = 1_234L)

        assertEquals("Channel", copied.mediaMetadata.title)
        assertEquals(1_234L, copied.mediaMetadata.durationMs)
        assertNotNull(copied.mediaMetadata.extras)
    }
}
