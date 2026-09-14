package dev.anilbeesetti.nextplayer.feature.player.service

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakeMediaRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class PlaybackHistoryTest {
    @Test
    fun readyQueueItemsRecordHistoryWithoutAnotherFirstFrame() {
        val recorded = mutableListOf<Pair<String, Long?>>()
        val service = PlayerService().apply {
            mediaRepository = object : MediaRepository by FakeMediaRepository() {
                override suspend fun updateMediumLastPlayedTime(uri: String, lastPlayedTime: Long, duration: Long?) {
                    assertTrue(lastPlayedTime > 0)
                    recorded += uri to duration
                }
            }
        }
        val listener = PlayerService::class.java.getDeclaredField("playbackStateListener")
            .apply { isAccessible = true }.get(service) as Player.Listener
        val exoPlayer = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        var state = Player.STATE_BUFFERING
        var duration = 20_000L
        val player = object : ForwardingPlayer(exoPlayer) {
            override fun getPlaybackState(): Int = state
            override fun getDuration(): Long = duration
        }
        fun notify(event: Int) {
            listener.onEvents(player, Player.Events(FlagSet.Builder().add(event).build()))
            shadowOf(Looper.getMainLooper()).idle()
        }
        try {
            val items = listOf("first", "second").map {
                MediaItem.Builder().setMediaId(it).setUri("webdav://example.com/$it.mp4?cid=1").build()
            }
            player.setMediaItems(items)
            notify(Player.EVENT_MEDIA_ITEM_TRANSITION)
            assertTrue(recorded.isEmpty())

            state = Player.STATE_READY
            notify(Player.EVENT_PLAYBACK_STATE_CHANGED)
            assertEquals(listOf("first" to 20_000L), recorded)
            assertEquals(20_000L, player.currentMediaItem?.mediaMetadata?.durationMs)

            player.seekTo(1, 0)
            duration = 180_000L
            notify(Player.EVENT_MEDIA_ITEM_TRANSITION)
            assertEquals(listOf("first" to 20_000L, "second" to 180_000L), recorded)
            assertEquals(180_000L, player.currentMediaItem?.mediaMetadata?.durationMs)

            notify(Player.EVENT_MEDIA_METADATA_CHANGED)
            assertEquals(2, recorded.size)

            duration = C.TIME_UNSET
            notify(Player.EVENT_PLAYBACK_STATE_CHANGED)
            assertEquals("second" to null, recorded.last())
        } finally {
            player.release()
        }
    }
}
