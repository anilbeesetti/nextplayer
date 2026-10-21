package dev.anilbeesetti.nextplayer.feature.player.service

import android.os.Bundle
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakeMediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakePreferencesRepository
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClientFactory
import dev.anilbeesetti.nextplayer.core.media.network.NetworkConnectionResolver
import dev.anilbeesetti.nextplayer.core.media.network.datasource.NetworkSessions
import dev.anilbeesetti.nextplayer.core.media.network.datasource.NextDataSourceFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlaybackSpeedSessionTest {
    private val preferences = FakePreferencesRepository()

    @Before
    fun setUp() {
        runBlocking { preferences.updatePlayerPreferences { it.copy(defaultPlaybackSpeed = 1.25f) } }
        startKoin {
            modules(
                module {
                    single<PreferencesRepository> { preferences }
                    single<MediaRepository> { FakeMediaRepository() }
                    single {
                        NextDataSourceFactory(
                            RuntimeEnvironment.getApplication(),
                            NetworkSessions(
                                NetworkConnectionResolver { null },
                                NetworkClientFactory { error("No network playback in this test") },
                            ),
                        )
                    }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun sessionStartsAtPreferenceAndIgnoresLegacyVideoSpeed() = withSession { _, player ->
        assertEquals(1.25f, player.playbackParameters.speed)
        player.setMediaItems(listOf(video("first", 0.5f), video("second", 3f)))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1.25f, player.playbackParameters.speed)
        player.seekToNextMediaItem()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1.25f, player.playbackParameters.speed)
    }

    @Test
    fun changedSpeedSurvivesNavigationRepeatAndQueueReplacement() = withSession { service, player ->
        player.setMediaItems(listOf(video("first", 0.5f), video("second", 3f)))
        shadowOf(Looper.getMainLooper()).idle()
        player.setPlaybackSpeed(1.75f)
        player.seekToNextMediaItem()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1.75f, player.playbackParameters.speed)
        player.seekToPreviousMediaItem()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1.75f, player.playbackParameters.speed)

        val listener = PlayerService::class.java.getDeclaredField("playbackStateListener")
            .apply { isAccessible = true }.get(service) as Player.Listener
        listener.onMediaItemTransition(player.currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        listener.onMediaItemTransition(player.currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
        listener.onPlaybackStateChanged(Player.STATE_ENDED)
        assertEquals(1.75f, player.playbackParameters.speed)

        player.pause()
        player.clearMediaItems()
        listener.onPlaybackStateChanged(Player.STATE_IDLE)
        player.setMediaItem(video("replacement", 2f))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1.75f, player.playbackParameters.speed)
        assertEquals(1.25f, preferences.playerPreferences.value.defaultPlaybackSpeed)
    }

    @Test
    fun newSessionUsesPreferenceInsteadOfPreviousSessionSpeed() {
        withSession { _, player ->
            player.setMediaItem(video("first", 0.5f))
            player.setPlaybackSpeed(1.75f)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1.75f, player.playbackParameters.speed)
        }
        runBlocking { preferences.updatePlayerPreferences { it.copy(defaultPlaybackSpeed = 1.5f) } }
        withSession { _, player ->
            player.setMediaItem(video("first", 0.5f))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1.5f, player.playbackParameters.speed)
        }
    }

    private fun withSession(check: (PlayerService, Player) -> Unit) {
        val controller = Robolectric.buildService(PlayerService::class.java).create()
        val service = controller.get()
        val session = PlayerService::class.java.getDeclaredField("mediaSession")
            .apply { isAccessible = true }.get(service) as MediaSession
        try {
            check(service, session.player)
        } finally {
            controller.destroy()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun video(id: String, legacySpeed: Float): MediaItem = MediaItem.Builder()
        .setMediaId("file:///$id.mp4")
        .setUri("file:///$id.mp4")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setArtworkData(byteArrayOf(1), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                .setExtras(Bundle().apply { putFloat("media_metadata_playback_speed", legacySpeed) })
                .build(),
        )
        .build()
}
