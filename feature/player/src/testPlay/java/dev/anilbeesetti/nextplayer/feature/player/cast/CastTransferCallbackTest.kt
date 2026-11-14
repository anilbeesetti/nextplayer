package dev.anilbeesetti.nextplayer.feature.player.cast

import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.Size
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakePreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.Resume
import dev.anilbeesetti.nextplayer.feature.player.MiniPlayerTestPlayer
import dev.anilbeesetti.nextplayer.feature.player.extensions.copy
import dev.anilbeesetti.nextplayer.feature.player.extensions.positionMs
import dev.anilbeesetti.nextplayer.feature.player.extensions.videoDecoderMode
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CastTransferCallbackTest {
    private val local = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
    private val remote = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
    private val items = (0..2).map {
        MediaItem.Builder().setMediaId("$it").setUri("file:///$it.mp4").build()
    }

    @Test
    fun repeatedTransferDoesNotReapplyStaleResumeMetadata() {
        val preferences = FakePreferencesRepository()
        runBlocking { preferences.updatePlayerPreferences { it.copy(resume = Resume.YES) } }
        startKoin { modules(module { single<PreferencesRepository> { preferences } }) }
        val player = object : ForwardingPlayer(remote) {
            override fun getDeviceInfo() =
                DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build()
        }
        val session = MediaSession.Builder(
            RuntimeEnvironment.getApplication(),
            player,
        ).build()
        val service = PlayerService()
        fun field(name: String) = PlayerService::class.java.getDeclaredField(name)
            .apply { isAccessible = true }
        field("mediaSession").set(
            service,
            session,
        )
        val listener = field("playbackStateListener").get(service) as Player.Listener
        val callback = CastTransferCallback(remote) { false }
        try {
            local.setMediaItems(
                items.map { it.copy(positionMs = 58_000) },
                1,
                152_000,
            )
            callback.transferState(
                local,
                remote,
            )
            callback.transferState(
                remote,
                local,
            )
            local.seekTo(165_000)
            callback.transferState(
                local,
                remote,
            )
            // Receiver acknowledgments can follow transient folder items while route info lags.
            field("lastPlaybackMediaId").set(
                service,
                items[0].mediaId,
            )
            field("lastPlaybackType").set(
                service,
                DeviceInfo.PLAYBACK_TYPE_REMOTE,
            )
            listener.onMediaItemTransition(
                remote.currentMediaItem,
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
            )
            assertEquals(
                165_000L,
                remote.currentPosition,
            )
            remote.seekTo(170_000)
            listener.onMediaItemTransition(
                remote.currentMediaItem,
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
            )
            assertEquals(
                170_000L,
                remote.currentPosition,
            )
            callback.transferState(
                remote,
                local,
            )
            assertEquals(
                170_000L,
                local.currentPosition,
            )
            assertEquals(
                3,
                local.mediaItemCount,
            )
            assertNull(local.currentMediaItem?.mediaMetadata?.positionMs)
            assertEquals(
                58_000L,
                local.getMediaItemAt(0).mediaMetadata.positionMs,
            )
            remote.setMediaItem(items[0].copy(positionMs = 60_000))
            listener.onMediaItemTransition(
                remote.currentMediaItem,
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
            )
            assertEquals(
                60_000L,
                remote.currentPosition,
            )
        } finally {
            session.release()
            stopKoin()
        }
    }

    @Test
    fun dismissalSnapshotDropsConsumedHintAfterRemoteQueueAdvances() {
        val callback = CastTransferCallback(remote) { true }
        local.setMediaItems(
            items.map { it.copy(positionMs = 58_000) },
            0,
            0,
        )
        callback.transferState(
            local,
            remote,
        )
        remote.seekTo(
            1,
            35_000,
        )
        val saved = callback.snapshotForPhone(remote)
        assertEquals(
            35_000,
            saved.currentPosition,
        )
        assertNull(saved.mediaItems[1].mediaMetadata.positionMs)
        assertEquals(
            58_000L,
            saved.mediaItems[2].mediaMetadata.positionMs,
        )
    }

    @After
    fun tearDown() {
        local.release()
        remote.release()
    }

    @Test
    fun explicitStopDoesNotRestoreAnAsynchronouslyClearedRemoteQueue() {
        val callback = CastTransferCallback(remote) { false }
        local.setMediaItems(
            items,
            1,
            12_000,
        )
        local.playWhenReady = true
        callback.transferState(
            local,
            remote,
        )
        callback.onStop()
        callback.clearSavedQueue()
        // The receiver can still report its old queue when the Cast session disconnects.
        remote.stop()
        assertEquals(
            1,
            remote.mediaItemCount,
        )
        callback.transferState(
            remote,
            local,
        )
        assertEquals(
            0,
            local.mediaItemCount,
        )
        assertFalse(local.playWhenReady)

        local.setMediaItem(items[2])
        callback.transferState(
            local,
            remote,
        )
        callback.transferState(
            remote,
            local,
        )
        assertEquals(
            "2",
            local.currentMediaItem?.mediaId,
        )
    }

    @Test
    fun newQueueAcceptedWhileDisconnectingSurvivesTheStop() {
        val callback = CastTransferCallback(remote) { false }
        local.setMediaItems(
            items,
            1,
            12_000,
        )
        callback.transferState(
            local,
            remote,
        )
        callback.onStop()
        val next = MediaItem.Builder().setMediaId("new")
            .setUri("https://example.com/new.mp4").build()
        remote.setMediaItem(
            next,
            34_000,
        )
        callback.onQueueReady()
        remote.playWhenReady = true
        callback.transferState(
            remote,
            local,
        )
        assertEquals(
            "new",
            local.currentMediaItem?.mediaId,
        )
        assertEquals(
            34_000L,
            local.currentPosition,
        )
        assertEquals(
            true,
            local.playWhenReady,
        )
    }

    @Test
    fun leavingPhoneDetachesSurfaceBeforeItsViewIsDestroyed() {
        val texture = SurfaceTexture(0)
        val surface = Surface(texture)
        val callback = CastTransferCallback(remote) { false }
        try {
            local.setMediaItems(
                items,
                1,
                34_000,
            )
            local.setVideoSurface(surface)
            assertNotEquals(
                Size.ZERO,
                local.surfaceSize,
            )
            callback.transferState(
                local,
                remote,
            )
            assertEquals(
                Size.ZERO,
                local.surfaceSize,
            )
            surface.release()
            callback.transferState(
                remote,
                local,
            )
            assertEquals(
                "1",
                local.currentMediaItem?.mediaId,
            )
            assertEquals(
                34_000L,
                local.currentPosition,
            )
        } finally {
            surface.release()
            texture.release()
        }
    }

    @Test
    fun autoplayOffCastsSelectedItemAndRestoresFolderAtLivePosition() {
        val callback = CastTransferCallback(remote) { false }
        local.setMediaItems(
            items,
            1,
            12_000,
        )
        local.playWhenReady = true
        callback.transferState(
            local,
            remote,
        )
        assertEquals(
            1,
            remote.mediaItemCount,
        )
        assertEquals(
            "1",
            remote.currentMediaItem?.mediaId,
        )
        assertEquals(
            12_000L,
            remote.currentPosition,
        )
        remote.seekTo(45_000)
        val saved = callback.snapshotForPhone(remote)
        callback.transferState(
            remote,
            local,
        )
        assertEquals(
            3,
            local.mediaItemCount,
        )
        assertEquals(
            1,
            local.currentMediaItemIndex,
        )
        assertEquals(
            45_000L,
            local.currentPosition,
        )
        assertEquals(
            saved.mediaItems,
            List(
                local.mediaItemCount,
                local::getMediaItemAt,
            ),
        )
        assertEquals(
            saved.currentMediaItemIndex,
            local.currentMediaItemIndex,
        )
        assertEquals(
            saved.playWhenReady,
            local.playWhenReady,
        )
        assertEquals(
            true,
            local.playWhenReady,
        )
    }

    @Test
    fun returnToPhoneKeepsMedia3PlaceholderFilteringAndLivePosition() {
        val receiver = MiniPlayerTestPlayer()
        try {
            receiver.update {
                setPlaylist(
                    listOf(
                        receiver.item(0),
                        SimpleBasePlayer.MediaItemData.Builder("placeholder")
                            .setMediaItem(MediaItem.Builder().setMediaId("placeholder").build())
                            .build(),
                        receiver.item(2),
                    ),
                )
                setCurrentMediaItemIndex(2)
                setContentPositionMs(35_000)
            }
            CastTransferCallback(receiver) { true }.transferState(
                receiver,
                local,
            )
            assertEquals(
                2,
                local.mediaItemCount,
            )
            assertEquals(
                1,
                local.currentMediaItemIndex,
            )
            assertEquals(
                "2",
                local.currentMediaItem?.mediaId,
            )
            assertEquals(
                35_000L,
                local.currentPosition,
            )
        } finally {
            receiver.release()
        }
    }

    @Test
    fun newRemoteQueueDoesNotRestorePreviousFolder() {
        val callback = CastTransferCallback(remote) { false }
        local.setMediaItems(
            items,
            1,
            0,
        )
        callback.transferState(
            local,
            remote,
        )
        callback.clearSavedQueue()
        remote.setMediaItems(
            listOf(items[2]),
            0,
            22_000,
        )
        callback.transferState(
            remote,
            local,
        )
        assertEquals(
            1,
            local.mediaItemCount,
        )
        assertEquals(
            "2",
            local.currentMediaItem?.mediaId,
        )
        assertEquals(
            22_000L,
            local.currentPosition,
        )
    }

    @Test
    fun advancingRemotelyTransfersCurrentItemsDecoderMetadataAndPosition() {
        val callback = CastTransferCallback(remote) { true }
        val second = items[1].copy(
            positionMs = 58_000,
            videoDecoderMode = DecoderMode.SOFTWARE,
        )
        local.setMediaItems(
            listOf(
                items[0],
                second,
            ),
            0,
            0,
        )
        callback.transferState(
            local,
            remote,
        )
        remote.seekTo(
            1,
            35_000,
        )
        remote.pause()
        callback.transferState(
            remote,
            local,
        )
        assertEquals(
            "1",
            local.currentMediaItem?.mediaId,
        )
        assertEquals(
            DecoderMode.SOFTWARE,
            local.currentMediaItem?.mediaMetadata?.videoDecoderMode,
        )
        assertEquals(
            35_000L,
            local.currentPosition,
        )
        assertNull(local.currentMediaItem?.mediaMetadata?.positionMs)
        assertEquals(
            58_000L,
            second.mediaMetadata.positionMs,
        )
        assertEquals(
            false,
            local.playWhenReady,
        )
        assertEquals(
            Player.STATE_IDLE,
            local.playbackState,
        )
    }
}
