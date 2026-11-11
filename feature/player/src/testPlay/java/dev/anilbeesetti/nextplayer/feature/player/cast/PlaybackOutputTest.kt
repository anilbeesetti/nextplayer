package dev.anilbeesetti.nextplayer.feature.player.cast

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import dev.anilbeesetti.nextplayer.feature.player.MiniPlayerTestPlayer
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackConnection
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackPresentation
import dev.anilbeesetti.nextplayer.feature.player.service.playbackTransferState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlaybackOutputTest {
    private val local = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
    private val remote = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
    private val publications = mutableListOf<PlaybackPresentation>()
    private val output = PlaybackOutput(
        RuntimeEnvironment.getApplication(),
        local,
        { true },
        publications::add,
    )
    private val callback = CastTransferCallback(remote) { true }
    private val session = mock(CastSession::class.java)

    @Suppress("UNCHECKED_CAST")
    private val listener = field(
        "sessionListener",
    ).get(output) as SessionManagerListener<CastSession>
    private val items = (0..2).map {
        MediaItem.Builder().setMediaId("$it").setUri("file:///$it.mp4").build()
    }

    @After fun tearDown() {
        output.release()
        remote.release()
    }

    @Test fun selectingReceiverImmediatelyPausesAndCancelRestoresLocalIntent() {
        listOf(
            false,
            true,
        ).forEach { playing ->
            local.setMediaItems(
                items,
                1,
                32_000,
            )
            local.playWhenReady = playing
            listener.onSessionStarting(session)
            assertFalse(local.playWhenReady)
            assertEquals(
                PlaybackConnection.CONNECTING,
                publications.last().connection,
            )
            assertEquals(
                "1",
                publications.last().mediaItem?.mediaId,
            )
            output.cancelConnection()
            assertEquals(
                32_000,
                local.currentPosition,
            )
            assertEquals(
                playing,
                local.playWhenReady,
            )
            assertEquals(
                PlaybackConnection.LOCAL,
                publications.last().connection,
            )
        }
    }

    @Test fun unexpectedLossTransfersLiveQueueToPhonePaused() {
        local.setMediaItems(
            items,
            0,
            1_000,
        )
        local.playWhenReady = true
        listener.onSessionStarting(session)
        output.transferState(
            local,
            remote,
            remote,
            callback,
        )
        remote.seekTo(
            2,
            65_000,
        )
        remote.playWhenReady = true
        field("presentation").set(
            output,
            PlaybackPresentation(PlaybackConnection.CAST),
        )
        output.transferState(
            remote,
            local,
            remote,
            callback,
        )
        assertEquals(
            2,
            local.currentMediaItemIndex,
        )
        assertEquals(
            65_000,
            local.currentPosition,
        )
        assertFalse(local.playWhenReady)
        assertEquals(
            PlaybackConnection.LOST,
            publications.last().connection,
        )
    }

    @Test fun failedAutomaticRecoveryKeepsPhonePaused() {
        local.setMediaItems(
            items,
            1,
            30_000,
        )
        local.playWhenReady = true
        field("lastRemote").set(
            output,
            playbackTransferState(local),
        )
        local.pause()
        field("presentation").set(
            output,
            PlaybackPresentation(PlaybackConnection.LOST),
        )
        listener.onSessionResuming(
            session,
            "saved",
        )
        output.cancelConnection()
        assertFalse(local.playWhenReady)
        assertEquals(
            30_000,
            local.currentPosition,
        )
    }

    @Test fun lateTransferAfterCancelCannotRestartReceiverOrOverwritePhone() {
        local.setMediaItems(
            items,
            1,
            30_000,
        )
        local.playWhenReady = true
        listener.onSessionStarting(session)
        output.cancelConnection()
        output.transferState(
            local,
            remote,
            remote,
            callback,
        )
        assertEquals(
            0,
            remote.mediaItemCount,
        )
        assertTrue(local.playWhenReady)
        assertEquals(
            30_000,
            local.currentPosition,
        )
    }

    @Test fun stoppedReceiverCannotReviveQueueAfterLocalPausedUndo() {
        local.setMediaItems(
            items,
            1,
            20_000,
        )
        val undo = playbackTransferState(local).buildUpon().setPlayWhenReady(false).build()
        output.transferState(
            local,
            remote,
            remote,
            callback,
        )
        output.onStop()
        output.restoreOnPhone(undo)
        local.seekTo(
            1,
            23_000,
        )
        remote.seekTo(
            0,
            88_000,
        )
        remote.playWhenReady = true
        output.transferState(
            remote,
            local,
            remote,
            callback,
        )
        assertEquals(
            1,
            local.currentMediaItemIndex,
        )
        output.transferState(
            remote,
            local,
            remote,
            callback,
        )
        assertEquals(
            23_000,
            local.currentPosition,
        )
        assertFalse(local.playWhenReady)
    }

    @Test fun newLocalQueueClearsLostRecoveryState() {
        local.setMediaItems(items)
        listener.onSessionSuspended(
            session,
            1,
        )
        output.onNewQueue()
        output.onQueueReady("0")
        assertEquals(
            PlaybackConnection.LOCAL,
            publications.last().connection,
        )
    }

    @Test fun readinessRequiresExpectedAcknowledgedMediaAndCommandsButNotPlaying() {
        val fake = MiniPlayerTestPlayer()
        try {
            fake.remote(true)
            assertFalse(fake.playWhenReady)
            assertTrue(
                isAcknowledgedCastMedia(
                    fake,
                    "0",
                ),
            )
            assertFalse(
                isAcknowledgedCastMedia(
                    fake,
                    "stale",
                ),
            )
            fake.update { setPlaybackState(Player.STATE_BUFFERING) }
            assertFalse(
                isAcknowledgedCastMedia(
                    fake,
                    "0",
                ),
            )
            fake.update {
                setPlaybackState(Player.STATE_READY)
                setAvailableCommands(Player.Commands.EMPTY)
            }
            assertFalse(
                isAcknowledgedCastMedia(
                    fake,
                    "0",
                ),
            )
        } finally {
            fake.release()
        }
    }

    @Test fun receiverSnapshotTracksItemChangesAndLivePosition() {
        val fake = MiniPlayerTestPlayer()
        try {
            fake.remote(true)
            field("remotePlayer").set(
                output,
                fake,
            )
            field("transfer").set(
                output,
                CastTransferCallback(fake) { true },
            )
            field("expectedMediaId").set(
                output,
                "0",
            )
            field("presentation").set(
                output,
                PlaybackPresentation(PlaybackConnection.CONNECTING),
            )
            listener.onSessionStarted(
                session,
                "connected",
            )
            fake.update {
                setCurrentMediaItemIndex(1)
                setContentPositionMs(44_000)
            }
            listener.onSessionStarted(
                session,
                "connected",
            )
            val retained = field(
                "lastRemote",
            ).get(output) as androidx.media3.common.PlayerTransferState
            assertEquals(
                "1",
                retained.mediaItems[retained.currentMediaItemIndex].mediaId,
            )
            assertEquals(
                44_000,
                retained.currentPosition,
            )
        } finally {
            fake.release()
        }
    }

    @Test fun connectsOnTheReceiverItemBeforeMedia3QueueCatchesUp() {
        val fake = MiniPlayerTestPlayer()
        try {
            fake.remote(true)
            field("remotePlayer").set(
                output,
                fake,
            )
            field("transfer").set(
                output,
                CastTransferCallback(fake) { true },
            )
            field("expectedMediaId").set(
                output,
                "1",
            )
            field("presentation").set(
                output,
                PlaybackPresentation(PlaybackConnection.CONNECTING),
            )
            val receiverPlays: () -> String? = { "1" }
            field("receiverMediaId").set(
                output,
                receiverPlays,
            )
            // Media3 still names entry 0 while the receiver already plays entry 1.
            listener.onSessionStarted(
                session,
                "connected",
            )
            val connected = publications.last()
            assertEquals(
                PlaybackConnection.CAST,
                connected.connection,
            )
            assertEquals(
                "1",
                connected.mediaItem?.mediaId,
            )
            fake.update { setCurrentMediaItemIndex(1) }
            listener.onSessionStarted(
                session,
                "connected",
            )
            assertEquals(
                "1",
                publications.last().mediaItem?.mediaId,
            )
        } finally {
            fake.release()
        }
    }

    @Test fun receiverIsLoadedPausedAtTheStartAndThenPlaced() {
        local.setMediaItems(
            items,
            1,
            30_000,
        )
        local.playWhenReady = true
        listener.onSessionStarting(session)
        output.transferState(
            local,
            remote,
            remote,
            callback,
        )
        // Loading at a position starts this receiver unreliably; seeking a loaded item does not.
        assertEquals(
            0,
            remote.currentPosition,
        )
        assertFalse(remote.playWhenReady)
        assertEquals(
            30_000L,
            field("sentPositionMs").get(output),
        )
    }

    @Test fun loadedItemIsPlacedAndResumedBeforeCasting() {
        val fake = MiniPlayerTestPlayer()
        try {
            fake.remote(true)
            fake.update { setContentPositionMs(0) }
            field("remotePlayer").set(
                output,
                fake,
            )
            field("transfer").set(
                output,
                CastTransferCallback(fake) { true },
            )
            field("expectedMediaId").set(
                output,
                "0",
            )
            field("presentation").set(
                output,
                PlaybackPresentation(PlaybackConnection.CONNECTING),
            )
            field("outboundPlaying").set(
                output,
                true,
            )
            field("sentPositionMs").set(
                output,
                42_000L,
            )
            listener.onSessionStarted(
                session,
                "connected",
            )
            assertEquals(
                42_000,
                fake.currentPosition,
            )
            assertTrue(fake.playWhenReady)
            assertTrue(publications.none { it.connection == PlaybackConnection.CAST })
            listener.onSessionStarted(
                session,
                "connected",
            )
            assertEquals(
                PlaybackConnection.CAST,
                publications.last().connection,
            )
        } finally {
            fake.release()
        }
    }

    @Test fun secondExplicitSelectionAfterCancelCanBecomeReady() {
        local.setMediaItems(
            items,
            0,
            10_000,
        )
        listener.onSessionStarting(session)
        output.cancelConnection()
        val fake = MiniPlayerTestPlayer()
        try {
            fake.remote(true)
            field("remotePlayer").set(
                output,
                fake,
            )
            field("transfer").set(
                output,
                CastTransferCallback(fake) { true },
            )
            listener.onSessionStarting(session)
            listener.onSessionStarted(
                session,
                "second",
            )
            assertEquals(
                PlaybackConnection.CAST,
                publications.last().connection,
            )
        } finally {
            fake.release()
        }
    }

    @Test fun placeholderDuringNextItemLoadingDoesNotReplaceRecoveryQueue() {
        local.setMediaItems(
            items,
            1,
            38_000,
        )
        field("lastRemote").set(
            output,
            playbackTransferState(local),
        )
        field("presentation").set(
            output,
            PlaybackPresentation(PlaybackConnection.CAST),
        )
        val fake = MiniPlayerTestPlayer()
        try {
            fake.update {
                setPlaylist(
                    listOf(
                        SimpleBasePlayer.MediaItemData.Builder("placeholder")
                            .setMediaItem(
                                MediaItem.Builder().setMediaId("placeholder").build(),
                            ).build(),
                    ),
                )
            }
            output.transferState(
                fake,
                local,
                fake,
                CastTransferCallback(fake) { true },
            )
            assertEquals(
                "1",
                local.currentMediaItem?.mediaId,
            )
            assertEquals(
                38_000,
                local.currentPosition,
            )
            assertFalse(local.playWhenReady)
        } finally {
            fake.release()
        }
    }

    private fun field(name: String) = PlaybackOutput::class.java.getDeclaredField(name)
        .apply { isAccessible = true }
}
