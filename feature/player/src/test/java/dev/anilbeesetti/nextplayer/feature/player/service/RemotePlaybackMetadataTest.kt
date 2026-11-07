package dev.anilbeesetti.nextplayer.feature.player.service

import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import dev.anilbeesetti.nextplayer.feature.player.extensions.copy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RemotePlaybackMetadataTest {
    @Test
    fun unresolvedHandoffDoesNotLoadArtworkOrRestoreStaleResumePosition() {
        val context = RuntimeEnvironment.getApplication()
        val exo = ExoPlayer.Builder(context).build()
        var seeks = 0
        val player = object : ForwardingPlayer(exo) {
            override fun getDeviceInfo() =
                DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build()
            override fun seekTo(positionMs: Long) {
                seeks++
            }
        }
        val session = MediaSession.Builder(
            context,
            player,
        ).build()
        val service = PlayerService()
        fun field(name: String) = PlayerService::class.java.getDeclaredField(name)
            .apply { isAccessible = true }
        field("mediaSession").set(
            service,
            session,
        )
        val item = MediaItem.fromUri("content://media/42").copy(positionMs = 99_000)
        field("lastPlaybackMediaId").set(
            service,
            item.mediaId,
        )
        field("lastPlaybackType").set(
            service,
            DeviceInfo.PLAYBACK_TYPE_LOCAL,
        )
        val listener = field("playbackStateListener").get(service) as Player.Listener
        try {
            listener.onMediaItemTransition(
                null,
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
            )
            listener.onMediaItemTransition(
                MediaItem.EMPTY,
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
            )
            listener.onMediaItemTransition(
                item,
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
            )
            assertEquals(
                0,
                seeks,
            )
            assertNull(field("artworkLoadJob").get(service))
        } finally {
            session.release()
            exo.release()
        }
    }
}
