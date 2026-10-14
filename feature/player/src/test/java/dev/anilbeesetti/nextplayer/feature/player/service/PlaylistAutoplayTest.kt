package dev.anilbeesetti.nextplayer.feature.player.service

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakePreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.PlaylistItemRecord
import dev.anilbeesetti.nextplayer.core.model.PlaylistRecord
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import dev.anilbeesetti.nextplayer.feature.player.utils.toMediaQueue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class PlaylistAutoplayTest {
    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun explicitPlaylistAdvancesWithAutoplayOffInEveryRepeatMode() {
        withPlayer(autoplay = false) { player ->
            for (repeatMode in listOf(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ONE, Player.REPEAT_MODE_ALL)) {
                player.repeatMode = repeatMode
                player.setMediaItems(explicitPlaylist())
                assertFalse("Explicit playlist must advance in repeat mode $repeatMode", player.pauseAtEndOfMediaItems)
            }
        }
    }

    @Test
    fun automaticFolderQueueStillPausesWithAutoplayOff() {
        withPlayer(autoplay = false) { player ->
            player.setMediaItems(automaticQueue())
            assertTrue(player.pauseAtEndOfMediaItems)
        }
    }

    @Test
    fun replacingExplicitPlaylistRestoresAutoplayOffForAutomaticQueue() {
        withPlayer(autoplay = false) { player ->
            player.setMediaItems(explicitPlaylist())
            assertFalse(player.pauseAtEndOfMediaItems)
            player.setMediaItems(automaticQueue())
            assertTrue(player.pauseAtEndOfMediaItems)
            player.setMediaItems(explicitPlaylist())
            assertFalse(player.pauseAtEndOfMediaItems)
        }
    }

    @Test
    fun autoplayOnAdvancesBothQueueTypes() {
        withPlayer(autoplay = true) { player ->
            player.setMediaItems(automaticQueue())
            assertFalse(player.pauseAtEndOfMediaItems)
            player.setMediaItems(explicitPlaylist())
            assertFalse(player.pauseAtEndOfMediaItems)
        }
    }

    @Test
    fun editingExplicitPlaylistKeepsAutomaticTransitionsEnabled() {
        withPlayer(autoplay = false) { player ->
            player.setMediaItems(explicitPlaylist())
            player.moveMediaItem(0, 1)
            player.removeMediaItem(0)
            assertFalse(player.pauseAtEndOfMediaItems)
        }
    }

    private fun withPlayer(autoplay: Boolean, check: (ExoPlayer) -> Unit) {
        val preferences = FakePreferencesRepository()
        runBlocking { preferences.updatePlayerPreferences { it.copy(autoplay = autoplay) } }
        startKoin { modules(module { single<PreferencesRepository> { preferences } }) }
        val context = RuntimeEnvironment.getApplication()
        val player = ExoPlayer.Builder(context).build()
        val session = MediaSession.Builder(context, player).build()
        val service = PlayerService()
        PlayerService::class.java.getDeclaredField("mediaSession").apply { isAccessible = true }.set(service, session)
        val listener = PlayerService::class.java.getDeclaredField("playbackStateListener")
            .apply { isAccessible = true }.get(service) as Player.Listener
        // Exercise real timeline changes without initializing unrelated native decoder and artwork state.
        player.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                listener.onTimelineChanged(timeline, reason)
            }
        })
        player.pauseAtEndOfMediaItems = !autoplay
        try {
            check(player)
        } finally {
            session.release()
            player.release()
        }
    }

    private fun automaticQueue() = listOf("first", "second").map {
        MediaItem.Builder().setMediaId(it).setUri("file:///$it.mp4").build()
    }

    private fun explicitPlaylist() = PlaylistRecord(
        id = 7,
        name = "Selected videos",
        type = PlaylistType.M3U_URL,
        source = "https://example.com/list.m3u",
        items = listOf(
            PlaylistItemRecord(position = 0, uri = "file:///first.mp4"),
            PlaylistItemRecord(position = 1, uri = "file:///second.mp4"),
        ),
        lastRefreshedAt = 123,
    ).toMediaQueue("file:///first.mp4")!!.mediaItems
}
