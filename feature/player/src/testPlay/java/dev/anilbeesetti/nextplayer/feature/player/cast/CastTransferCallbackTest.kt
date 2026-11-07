package dev.anilbeesetti.nextplayer.feature.player.cast

import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.Size
import androidx.media3.exoplayer.ExoPlayer
import dev.anilbeesetti.nextplayer.feature.player.extensions.copy
import dev.anilbeesetti.nextplayer.feature.player.extensions.videoDecoderMode
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
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

    @After
    fun tearDown() {
        local.release()
        remote.release()
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
            true,
            local.playWhenReady,
        )
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
        val second = items[1].copy(videoDecoderMode = DecoderMode.SOFTWARE)
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
