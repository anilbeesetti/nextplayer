package dev.anilbeesetti.nextplayer.feature.player.service

import android.os.Looper
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakeMediaRepository
import dev.anilbeesetti.nextplayer.feature.player.extensions.copy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RemotePlaybackMetadataTest {
    @Test
    fun positionChangesIgnoreUnresolvedReceiverItems() {
        val recorded = mutableListOf<Pair<String, Long>>()
        startKoin {
            modules(
                module {
                    single<MediaRepository> {
                        object : MediaRepository by FakeMediaRepository() {
                            override suspend fun updateMediumPosition(uri: String, position: Long) {
                                recorded += uri to position
                            }
                        }
                    }
                },
            )
        }
        val context = RuntimeEnvironment.getApplication()
        val exo = ExoPlayer.Builder(context).build()
        var item = MediaItem.EMPTY
        val player = object : ForwardingPlayer(exo) {
            override fun getCurrentMediaItem() = item
            override fun getCurrentPosition() = 32_000L
        }
        val session = MediaSession.Builder(
            context,
            player,
        ).build()
        val service = PlayerService()
        PlayerService::class.java.getDeclaredField("mediaSession")
            .apply { isAccessible = true }.set(
                service,
                session,
            )
        val listener = PlayerService::class.java.getDeclaredField("playbackStateListener")
            .apply { isAccessible = true }.get(service) as Player.Listener
        try {
            listener.onIsPlayingChanged(false)
            item = MediaItem.Builder().setMediaId("content://media/42")
                .setUri("content://media/42").build()
            listener.onIsPlayingChanged(true)
            item = MediaItem.EMPTY
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(
                listOf("content://media/42" to 32_000L),
                recorded,
            )
        } finally {
            session.release()
            exo.release()
            stopKoin()
        }
    }

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
            assertNull(field("artworkLoadJob").get(service))
            // The resolved item may load the phone's thumbnail for the cast notification.
            listener.onMediaItemTransition(
                item,
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
            )
            assertEquals(
                0,
                seeks,
            )
        } finally {
            session.release()
            exo.release()
        }
    }
}
