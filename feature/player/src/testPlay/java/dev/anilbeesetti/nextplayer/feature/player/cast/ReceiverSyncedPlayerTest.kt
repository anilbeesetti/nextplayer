package dev.anilbeesetti.nextplayer.feature.player.cast

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import dev.anilbeesetti.nextplayer.feature.player.MiniPlayerTestPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class ReceiverSyncedPlayerTest {
    @Test fun presentsTheReceiverItemUntilMedia3QueueCatchesUp() {
        val media3 = MiniPlayerTestPlayer()
        val player = ReceiverSyncedPlayer(media3)
        try {
            val queue = listOf(
                "selected",
                "next",
            ).map { MediaItem.Builder().setMediaId(it).setUri("file:///$it.mp4").build() }
            player.showReceiver(
                queue,
                0,
                21_000,
            )
            ShadowLooper.idleMainLooper()
            assertEquals(
                "selected",
                player.currentMediaItem?.mediaId,
            )
            assertEquals(
                21_000,
                player.duration,
            )
            assertEquals(
                2,
                player.currentTimeline.windowCount,
            )
            assertEquals(
                10_000,
                player.currentPosition,
            )
            assertFalse(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
            player.seekTo(5_000)
            ShadowLooper.idleMainLooper()
            assertEquals(
                listOf(0),
                media3.seeks,
            )

            player.showReceiver(
                emptyList(),
                C.INDEX_UNSET,
                C.TIME_UNSET,
            )
            ShadowLooper.idleMainLooper()
            assertEquals(
                "0",
                player.currentMediaItem?.mediaId,
            )
            assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        } finally {
            player.release()
        }
    }
}
