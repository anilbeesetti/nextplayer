package dev.anilbeesetti.nextplayer.feature.player

import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackConnection
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackPresentation
import dev.anilbeesetti.nextplayer.feature.player.state.PlaybackSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackPresentationTest {
    @Test fun freshControlsRetainConnectingMediaAcrossAnEmptyReceiverTimeline() {
        val item = MediaItem.Builder().setMediaId("selected").setUri("content://media/video/1")
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle("Selected video").setDurationMs(60_000).build(),
            ).build()
        val extras = Bundle()
        PlaybackPresentation(
            PlaybackConnection.CONNECTING,
            "TV",
            1,
            item,
            31_000,
        ).writeTo(extras)
        val presentation = PlaybackPresentation.from(extras)
        val fake = MiniPlayerTestPlayer()
        try {
            fake.update {
                setPlaylist(emptyList()).setCurrentMediaItemIndex(C.INDEX_UNSET)
                    .setPlaybackState(Player.STATE_IDLE)
            }
            val empty = PlaybackSnapshot.from(fake)
            val connecting = empty.retainedFor(
                presentation,
                empty,
            )
            assertTrue(connecting.visible)
            assertEquals(
                "Selected video",
                connecting.metadata.title,
            )
            assertEquals(
                31_000,
                connecting.position,
            )
            assertEquals(
                60_000,
                connecting.duration,
            )
            assertEquals(
                1,
                presentation.expansionRequest,
            )
            assertFalse(
                connecting.retainedFor(
                    presentation.copy(connection = PlaybackConnection.STOPPED),
                    connecting,
                ).visible,
            )
        } finally {
            fake.release()
        }
    }

    @Test fun connectingKeepsTheCastItemWhileTheReceiverReportsAnotherQueueEntry() {
        val casting = MediaItem.Builder().setMediaId("selected").setUri("content://media/video/1")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Selected video").build()).build()
        val other = MediaItem.Builder().setMediaId("other").setUri("content://media/video/2")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Other video").build()).build()
        val fake = MiniPlayerTestPlayer()
        try {
            fake.update {
                setPlaylist(
                    listOf(
                        androidx.media3.common.SimpleBasePlayer.MediaItemData.Builder("other")
                            .setMediaItem(other).build(),
                    ),
                ).setCurrentMediaItemIndex(0).setPlaybackState(Player.STATE_BUFFERING)
            }
            val receiver = PlaybackSnapshot.from(fake)
            val shown = receiver.retainedFor(
                PlaybackPresentation(
                    PlaybackConnection.CONNECTING,
                    "TV",
                    mediaItem = casting,
                    position = 2_000,
                ),
                receiver,
            )
            assertEquals(
                "selected",
                shown.item?.mediaId,
            )
            assertEquals(
                "Selected video",
                shown.metadata.title,
            )
        } finally {
            fake.release()
        }
    }

    @Test fun castingShowsTheReceiverItemOnlyWhileMedia3QueueIsBehind() {
        val casting = MediaItem.Builder().setMediaId("selected").setUri("content://media/video/1")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Selected video").build()).build()
        val fake = MiniPlayerTestPlayer()
        try {
            fake.update { setContentPositionMs(8_000) }
            val receiver = PlaybackSnapshot.from(fake)
            val presentation = PlaybackPresentation(
                PlaybackConnection.CAST,
                "TV",
                mediaItem = casting,
                receiverAhead = true,
            )
            val behind = receiver.retainedFor(
                presentation,
                receiver,
            )
            assertEquals(
                "selected",
                behind.item?.mediaId,
            )
            assertEquals(
                8_000,
                behind.position,
            )
            assertEquals(
                "0",
                receiver.retainedFor(
                    presentation.copy(receiverAhead = false),
                    receiver,
                ).item?.mediaId,
            )
        } finally {
            fake.release()
        }
    }
}
