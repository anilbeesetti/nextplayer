package dev.anilbeesetti.nextplayer.feature.player.service

import android.os.Bundle
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakeMediaRepository
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
@Config(manifest = Config.NONE, sdk = [28])
class StopPlayerSessionTest {
    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun stoppingSessionSavesLatestPositionBeforeClearingMedia() {
        val saved = mutableListOf<Pair<String, Long>>()
        withSession(savePosition = { uri, position -> saved += uri to position }) { player, session, callback ->
            // Closing PiP sends this command after playback has advanced from its last saved position.
            player.setMediaItem(MediaItem.Builder().setMediaId(VIDEO_URI).setUri(VIDEO_URI).build())
            player.seekTo(120_000)

            val result = callback.onCustomCommand(session, controllerInfo(), CustomCommands.STOP_PLAYER_SESSION.sessionCommand, Bundle.EMPTY)
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(listOf(VIDEO_URI to 120_000L), saved)
            assertEquals(0, player.mediaItemCount)
            assertEquals(SessionResult.RESULT_SUCCESS, result.get().resultCode)
        }
    }

    @Test
    fun stoppingSessionWaitsForPositionPersistence() {
        val saveStarted = CompletableDeferred<Unit>()
        val finishSave = CompletableDeferred<Unit>()
        val saved = mutableListOf<Pair<String, Long>>()
        withSession(savePosition = { uri, position ->
            saveStarted.complete(Unit)
            finishSave.await()
            saved += uri to position
        }) { player, session, callback ->
            player.setMediaItem(MediaItem.Builder().setMediaId(VIDEO_URI).setUri(VIDEO_URI).build())
            player.seekTo(120_000)

            val result = callback.onCustomCommand(session, controllerInfo(), CustomCommands.STOP_PLAYER_SESSION.sessionCommand, Bundle.EMPTY)
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue(saveStarted.isCompleted)
            assertFalse(result.isDone)
            assertEquals(1, player.mediaItemCount)
            assertTrue(saved.isEmpty())

            finishSave.complete(Unit)
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(listOf(VIDEO_URI to 120_000L), saved)
            assertEquals(0, player.mediaItemCount)
            assertEquals(SessionResult.RESULT_SUCCESS, result.get().resultCode)
        }
    }

    @Test
    fun stoppingEmptySessionDoesNotWritePosition() {
        withSession(savePosition = { _, _ -> throw AssertionError("An empty queue has no position to save") }) { player, session, callback ->
            val result = callback.onCustomCommand(session, controllerInfo(), CustomCommands.STOP_PLAYER_SESSION.sessionCommand, Bundle.EMPTY)
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(0, player.mediaItemCount)
            assertEquals(SessionResult.RESULT_SUCCESS, result.get().resultCode)
        }
    }

    private fun withSession(
        savePosition: suspend (String, Long) -> Unit,
        check: (ExoPlayer, MediaSession, MediaSession.Callback) -> Unit,
    ) {
        startKoin {
            modules(
                module {
                    single<MediaRepository> {
                        object : MediaRepository by FakeMediaRepository() {
                            override suspend fun updateMediumPosition(uri: String, position: Long) = savePosition(uri, position)
                        }
                    }
                },
            )
        }
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        val session = MediaSession.Builder(RuntimeEnvironment.getApplication(), player).build()
        val service = PlayerService()
        PlayerService::class.java.getDeclaredField("mediaSession").apply { isAccessible = true }.set(service, session)
        val callback = PlayerService::class.java.getDeclaredField("mediaSessionCallback")
            .apply { isAccessible = true }.get(service) as MediaSession.Callback
        try {
            check(player, session, callback)
        } finally {
            session.release()
            player.release()
        }
    }

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

    private companion object {
        const val VIDEO_URI = "file:///video.mp4"
    }
}
