package dev.anilbeesetti.nextplayer.feature.player.utils

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerMediaInfoTest {
    @Test
    fun liveStreamShowsKnownResolutionWithoutInventingDurationOrLocation() {
        val player = mock(Player::class.java)
        `when`(player.currentMediaItem)
            .thenReturn(MediaItem.fromUri("https://example.com/live.m3u8"))
        `when`(player.mediaMetadata)
            .thenReturn(MediaMetadata.Builder().setTitle("Live stream").build())
        `when`(player.currentTracks).thenReturn(Tracks.EMPTY)
        `when`(player.duration).thenReturn(C.TIME_UNSET)
        `when`(player.videoSize).thenReturn(
            VideoSize(
                1280,
                720,
            ),
        )

        val info = requireNotNull(
            player.mediaInfoSnapshot(
                fallbackName = "Unknown",
                renamedName = null,
            ),
        )

        assertEquals(
            "Live stream",
            info.video.nameWithExtension,
        )
        assertEquals(
            "",
            info.video.formattedDuration,
        )
        assertEquals(
            "",
            info.video.parentPath,
        )
        assertEquals(
            1280,
            info.videoStream?.frameWidth,
        )
        assertNull(info.video.format)
    }
}
