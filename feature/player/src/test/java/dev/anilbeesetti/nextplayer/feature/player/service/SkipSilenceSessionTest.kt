package dev.anilbeesetti.nextplayer.feature.player.service

import android.os.Bundle
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakeMediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakePreferencesRepository
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClientFactory
import dev.anilbeesetti.nextplayer.core.media.network.NetworkConnectionResolver
import dev.anilbeesetti.nextplayer.core.media.network.datasource.NetworkSessions
import dev.anilbeesetti.nextplayer.core.media.network.datasource.NextDataSourceFactory
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
class SkipSilenceSessionTest {
    private val preferences = FakePreferencesRepository()

    @Before
    fun setUp() {
        startKoin {
            modules(
                modules = module {
                    single<PreferencesRepository> { preferences }
                    single<MediaRepository> { FakeMediaRepository() }
                    single {
                        NextDataSourceFactory(
                            RuntimeEnvironment.getApplication(),
                            sessions = NetworkSessions(
                                resolver = NetworkConnectionResolver { null },
                                clientFactory = NetworkClientFactory {
                                    error(message = "No network playback in this test")
                                },
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
    fun newSessionStartsWithSkipSilenceOff() = withSession { player, _, _ ->
        assertFalse(player.skipSilenceEnabled)
        assertFalse(preferences.playerPreferences.value.skipSilenceEnabled)
    }

    @Test
    fun skipSilenceIsRestoredInNextSessionAndKeptAcrossPlaylist() {
        withSession { player, session, callback ->
            setSkipSilence(
                callback = callback,
                session = session,
                enabled = true,
            )
            assertTrue(player.skipSilenceEnabled)
            assertTrue(preferences.playerPreferences.value.skipSilenceEnabled)
        }

        withSession { player, session, callback ->
            assertTrue(player.skipSilenceEnabled)
            player.setMediaItems(
                listOf(
                    video(id = "first"),
                    video(id = "second"),
                ),
            )
            player.seekToNextMediaItem()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(player.skipSilenceEnabled)

            setSkipSilence(
                callback = callback,
                session = session,
                enabled = false,
            )
        }

        withSession { player, _, _ ->
            assertFalse(player.skipSilenceEnabled)
        }
    }

    private fun setSkipSilence(
        callback: MediaSession.Callback,
        session: MediaSession,
        enabled: Boolean,
    ) {
        callback.onCustomCommand(
            session,
            controllerInfo(),
            CustomCommands.SET_SKIP_SILENCE_ENABLED.sessionCommand,
            Bundle().apply {
                putBoolean(
                    CustomCommands.SKIP_SILENCE_ENABLED_KEY,
                    enabled,
                )
            },
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun withSession(check: (ExoPlayer, MediaSession, MediaSession.Callback) -> Unit) {
        val controller = Robolectric.buildService(PlayerService::class.java).create()
        val service = controller.get()
        val session = PlayerService::class.java.getDeclaredField("mediaSession")
            .apply { isAccessible = true }.get(service) as MediaSession
        val callback = PlayerService::class.java.getDeclaredField("mediaSessionCallback")
            .apply { isAccessible = true }.get(service) as MediaSession.Callback
        try {
            check(
                session.player as ExoPlayer,
                session,
                callback,
            )
        } finally {
            controller.destroy()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun video(id: String): MediaItem = MediaItem.Builder()
        .setMediaId("file:///$id.mp4")
        .setUri("file:///$id.mp4")
        .build()

    private fun controllerInfo() = MediaSession.ControllerInfo.createTestOnlyControllerInfo(
        "test",
        0,
        0,
        0,
        0,
        true,
        Bundle.EMPTY,
        true,
    )
}
