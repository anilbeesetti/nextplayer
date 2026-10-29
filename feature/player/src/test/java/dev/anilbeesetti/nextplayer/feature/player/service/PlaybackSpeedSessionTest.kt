package dev.anilbeesetti.nextplayer.feature.player.service

import android.os.Bundle
import android.os.Looper
import androidx.media3.common.BundleListRetriever
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import coil3.ImageLoader
import com.google.common.util.concurrent.ListenableFuture
import dev.anilbeesetti.nextplayer.core.data.models.VideoState
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakeMediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakePreferencesRepository
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClientFactory
import dev.anilbeesetti.nextplayer.core.media.network.NetworkConnectionResolver
import dev.anilbeesetti.nextplayer.core.media.network.datasource.NetworkSessions
import dev.anilbeesetti.nextplayer.core.media.network.datasource.NextDataSourceFactory
import dev.anilbeesetti.nextplayer.feature.player.utils.PlayerApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class PlaybackSpeedSessionTest {
    private val preferences = FakePreferencesRepository()
    private var metadataGate: CompletableDeferred<Unit>? = null
    private var delayedMediaId: String? = null
    private var metadataStarted: CompletableDeferred<Unit>? = null

    @Before
    fun setUp() {
        runBlocking {
            preferences.updatePlayerPreferences { it.copy(defaultPlaybackSpeed = 1.25f) }
        }
        startKoin {
            modules(
                modules = module {
                    single<PreferencesRepository> { preferences }
                    single<MediaRepository> {
                        object : MediaRepository by FakeMediaRepository() {
                            override suspend fun getVideoState(uri: String): VideoState? {
                                if (delayedMediaId == null || delayedMediaId == uri) {
                                    metadataStarted?.complete(Unit)
                                    metadataGate?.await()
                                }
                                return null
                            }
                        }
                    }
                    single { ImageLoader.Builder(RuntimeEnvironment.getApplication()).build() }
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
    fun sessionStartsAtPreferenceAndIgnoresLegacyVideoSpeed() = withSession { _, player ->
        assertEquals(
            1.25f,
            player.playbackParameters.speed,
        )
        player.setMediaItems(
            listOf(
                video(
                    id = "first",
                    legacySpeed = 0.5f,
                ),
                video(
                    id = "second",
                    legacySpeed = 3f,
                ),
            ),
        )
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(
            1.25f,
            player.playbackParameters.speed,
        )
        player.seekToNextMediaItem()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(
            1.25f,
            player.playbackParameters.speed,
        )
    }

    @Test
    fun changedSpeedSurvivesNavigationRepeatAndQueueReplacement() = withSession { service, player ->
        player.setMediaItems(
            listOf(
                video(
                    id = "first",
                    legacySpeed = 0.5f,
                ),
                video(
                    id = "second",
                    legacySpeed = 3f,
                ),
            ),
        )
        shadowOf(Looper.getMainLooper()).idle()
        player.setPlaybackSpeed(1.75f)
        player.seekToNextMediaItem()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(
            1.75f,
            player.playbackParameters.speed,
        )
        player.seekToPreviousMediaItem()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(
            1.75f,
            player.playbackParameters.speed,
        )

        val listener = PlayerService::class.java.getDeclaredField("playbackStateListener")
            .apply { isAccessible = true }.get(service) as Player.Listener
        listener.onMediaItemTransition(
            player.currentMediaItem,
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
        )
        listener.onMediaItemTransition(
            player.currentMediaItem,
            Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT,
        )
        listener.onPlaybackStateChanged(Player.STATE_ENDED)
        assertEquals(
            1.75f,
            player.playbackParameters.speed,
        )

        player.pause()
        player.clearMediaItems()
        listener.onPlaybackStateChanged(Player.STATE_IDLE)
        player.setMediaItem(
            video(
                id = "replacement",
                legacySpeed = 2f,
            ),
        )
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(
            1.75f,
            player.playbackParameters.speed,
        )
        assertEquals(
            1.25f,
            preferences.playerPreferences.value.defaultPlaybackSpeed,
        )
    }

    @Test
    fun newSessionUsesPreferenceInsteadOfPreviousSessionSpeed() {
        withSession { _, player ->
            player.setMediaItem(
                video(
                    id = "first",
                    legacySpeed = 0.5f,
                ),
            )
            player.setPlaybackSpeed(1.75f)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(
                1.75f,
                player.playbackParameters.speed,
            )
        }
        runBlocking { preferences.updatePlayerPreferences { it.copy(defaultPlaybackSpeed = 1.5f) } }
        withSession { _, player ->
            player.setMediaItem(
                video(
                    id = "first",
                    legacySpeed = 0.5f,
                ),
            )
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(
                1.5f,
                player.playbackParameters.speed,
            )
        }
    }

    @Test
    fun backgroundRequestAppliesQueuePositionAndSpeedWithoutSavingPreferences() = withSession {
            service,
            player,
        ->
        val item = video(
            "requested",
            0.5f,
        )
        val result = backgroundCommand(
            service,
            backgroundArgs(
                listOf(item),
                0,
                12_000,
                1.5f,
            ),
        )
        awaitResult(result)
        assertEquals(
            SessionResult.RESULT_SUCCESS,
            result.get().resultCode,
        )
        assertEquals(
            item.localConfiguration!!.uri,
            player.currentMediaItem!!.localConfiguration!!.uri,
        )
        assertEquals(
            12_000L,
            player.currentPosition,
        )
        assertEquals(
            1.5f,
            player.playbackParameters.speed,
        )
        assertTrue(player.playWhenReady)
        assertEquals(
            1.25f,
            preferences.playerPreferences.value.defaultPlaybackSpeed,
        )
        assertFalse(preferences.playerPreferences.value.autoBackgroundPlay)
    }

    @Test
    fun backgroundHandoffWaitsForMetadataBeforeAcknowledgingTheRequest() = withSession {
            service,
            player,
        ->
        metadataGate = CompletableDeferred()
        val result = backgroundCommand(
            service,
            backgroundArgs(
                listOf(
                    video(
                        "delayed",
                        1f,
                    ),
                ),
                0,
                0,
                2f,
            ),
        )
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(result.isDone)
        assertEquals(
            0,
            player.mediaItemCount,
        )
        metadataGate!!.complete(Unit)
        awaitResult(result)
        assertEquals(
            SessionResult.RESULT_SUCCESS,
            result.get().resultCode,
        )
        assertEquals(
            "file:///delayed.mp4",
            player.currentMediaItem!!.mediaId,
        )
        assertEquals(
            2f,
            player.playbackParameters.speed,
        )
    }

    @Test
    fun backgroundRequestWithoutSpeedPreservesTheCurrentSessionSpeed() = withSession {
            service,
            player,
        ->
        player.setPlaybackSpeed(1.75f)
        val result = backgroundCommand(
            service,
            backgroundArgs(
                listOf(
                    video(
                        "next",
                        1f,
                    ),
                ),
                0,
                0,
                null,
            ),
        )
        awaitResult(result)
        assertEquals(
            SessionResult.RESULT_SUCCESS,
            result.get().resultCode,
        )
        assertEquals(
            1.75f,
            player.playbackParameters.speed,
        )
    }

    @Test
    fun invalidBackgroundRequestLeavesTheExistingSessionUntouched() = withSession {
            service,
            player,
        ->
        player.setMediaItem(
            video(
                "existing",
                1f,
            ),
        )
        player.setPlaybackSpeed(1.75f)
        val invalid = listOf(
            backgroundArgs(
                emptyList(),
                0,
                0,
                1.5f,
            ),
            backgroundArgs(
                listOf(
                    video(
                        "bad-index",
                        1f,
                    ),
                ),
                4,
                0,
                1.5f,
            ),
            backgroundArgs(
                listOf(
                    video(
                        "bad-speed",
                        1f,
                    ),
                ),
                0,
                0,
                Float.NaN,
            ),
        )
        invalid.forEach { args ->
            val result = backgroundCommand(
                service,
                args,
            )
            awaitResult(result)
            assertEquals(
                SessionResult.RESULT_ERROR_BAD_VALUE,
                result.get().resultCode,
            )
            assertEquals(
                "file:///existing.mp4",
                player.currentMediaItem!!.mediaId,
            )
            assertEquals(
                1.75f,
                player.playbackParameters.speed,
            )
            assertFalse(player.playWhenReady)
        }
    }

    @Test
    fun foregroundApiSpeedIsInitialOnlyAndDoesNotChangeTheDefault() = withSession { _, player ->
        val request = PlayerApi(
            android.content.Intent().putExtra(
                "playback_speed",
                1.5f,
            ),
        )
        request.applyPlaybackSpeed(player)
        assertEquals(
            1.5f,
            player.playbackParameters.speed,
        )
        player.setPlaybackSpeed(2f)
        player.setMediaItems(
            listOf(
                video(
                    "one",
                    0.5f,
                ),
                video(
                    "two",
                    3f,
                ),
            ),
        )
        player.seekToNextMediaItem()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(
            2f,
            player.playbackParameters.speed,
        )
        PlayerApi(android.content.Intent()).applyPlaybackSpeed(player)
        assertEquals(
            2f,
            player.playbackParameters.speed,
        )
        assertEquals(
            1.25f,
            preferences.playerPreferences.value.defaultPlaybackSpeed,
        )
    }

    @Test
    fun stoppingTheSessionCancelsAPendingBackgroundHandoff() = withSession { service, player ->
        metadataGate = CompletableDeferred()
        val pending = backgroundCommand(
            service,
            backgroundArgs(
                listOf(
                    video(
                        "pending",
                        1f,
                    ),
                ),
                0,
                0,
                1.5f,
            ),
        )
        shadowOf(Looper.getMainLooper()).idle()
        val session = PlayerService::class.java.getDeclaredField("mediaSession").apply {
            isAccessible =
                true
        }.get(service) as MediaSession
        val callback = PlayerService::class.java.getDeclaredField("mediaSessionCallback").apply {
            isAccessible =
                true
        }.get(service) as MediaSession.Callback
        val info = MediaSession.ControllerInfo.createTestOnlyControllerInfo(
            "test",
            0,
            0,
            0,
            0,
            true,
            Bundle.EMPTY,
            true,
        )
        val stopped = callback.onCustomCommand(
            session,
            info,
            CustomCommands.STOP_PLAYER_SESSION.sessionCommand,
            Bundle.EMPTY,
        )
        awaitResult(stopped)
        metadataGate!!.complete(Unit)
        awaitResult(pending)
        assertEquals(
            SessionResult.RESULT_ERROR_INVALID_STATE,
            pending.get().resultCode,
        )
        assertEquals(
            0,
            player.mediaItemCount,
        )
        assertFalse(player.playWhenReady)
    }

    @Test
    fun backgroundRequestCancelsPendingForegroundMetadata() = withSession { service, player ->
        val old = video(
            "pending-foreground",
            1f,
        )
        metadataGate = CompletableDeferred()
        metadataStarted = CompletableDeferred()
        delayedMediaId = old.mediaId
        val pending = foregroundRequest(
            service,
            old,
        )
        awaitMetadataStarted()
        val latest = video(
            "latest-background",
            1f,
        )
        val background = backgroundCommand(
            service,
            backgroundArgs(
                listOf(latest),
                0,
                0,
                1.5f,
            ),
        )
        awaitResult(background)
        assertTrue(pending.isCancelled)
        metadataGate!!.complete(Unit)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(
            latest.mediaId,
            player.currentMediaItem?.mediaId,
        )
        assertTrue(player.playWhenReady)
        assertEquals(
            1.5f,
            player.playbackParameters.speed,
        )
    }

    @Test
    fun stoppingTheSessionCancelsPendingForegroundMetadata() = withSession { service, player ->
        val old = video(
            "pending-foreground",
            1f,
        )
        metadataGate = CompletableDeferred()
        metadataStarted = CompletableDeferred()
        delayedMediaId = old.mediaId
        val pending = foregroundRequest(
            service,
            old,
        )
        awaitMetadataStarted()
        val stopped = command(
            service,
            CustomCommands.STOP_PLAYER_SESSION,
            Bundle.EMPTY,
        )
        awaitResult(stopped)
        assertTrue(pending.isCancelled)
        metadataGate!!.complete(Unit)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(
            0,
            player.mediaItemCount,
        )
        assertFalse(player.playWhenReady)
    }

    private fun foregroundRequest(service: PlayerService, item: MediaItem) =
        serviceCallback(service).onSetMediaItems(
            serviceSession(service),
            controllerInfo(),
            mutableListOf(item),
            0,
            0,
        )

    private fun awaitMetadataStarted() {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (metadataStarted?.isCompleted != true && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue(
            "Foreground metadata did not start",
            metadataStarted?.isCompleted == true,
        )
    }

    private fun backgroundArgs(items: List<MediaItem>, index: Int, position: Long, speed: Float?) =
        Bundle().apply {
            putBinder(
                "background_media_items",
                BundleListRetriever(
                    items.map {
                        it.toBundleIncludeLocalConfiguration()
                    },
                ),
            )
            putInt(
                "background_start_index",
                index,
            )
            putLong(
                "background_start_position_ms",
                position,
            )
            speed?.let {
                putFloat(
                    "playback_speed",
                    it,
                )
            }
        }

    private fun backgroundCommand(
        service: PlayerService,
        args: Bundle,
    ): ListenableFuture<SessionResult> = command(
        service,
        CustomCommands.START_BACKGROUND_PLAYBACK,
        args,
    )

    private fun command(service: PlayerService, command: CustomCommands, args: Bundle) =
        serviceCallback(service).onCustomCommand(
            serviceSession(service),
            controllerInfo(),
            command.sessionCommand,
            args,
        )

    private fun serviceSession(service: PlayerService) =
        PlayerService::class.java.getDeclaredField("mediaSession").apply {
            isAccessible = true
        }.get(service) as MediaSession

    private fun serviceCallback(service: PlayerService) =
        PlayerService::class.java.getDeclaredField("mediaSessionCallback").apply {
            isAccessible = true
        }.get(service) as MediaSession.Callback

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

    private fun awaitResult(result: ListenableFuture<SessionResult>) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!result.isDone && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue(
            "Background request did not complete",
            result.isDone,
        )
    }

    private fun withSession(check: (PlayerService, Player) -> Unit) {
        val controller = Robolectric.buildService(PlayerService::class.java).create()
        val service = controller.get()
        val session = PlayerService::class.java.getDeclaredField("mediaSession")
            .apply { isAccessible = true }.get(service) as MediaSession
        try {
            check(
                service,
                session.player,
            )
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
                .setArtworkData(
                    byteArrayOf(1),
                    MediaMetadata.PICTURE_TYPE_FRONT_COVER,
                )
                .setExtras(
                    Bundle().apply {
                        putFloat(
                            "media_metadata_playback_speed",
                            legacySpeed,
                        )
                    },
                )
                .build(),
        )
        .build()
}
