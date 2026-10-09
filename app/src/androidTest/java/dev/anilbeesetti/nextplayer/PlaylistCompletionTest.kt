package dev.anilbeesetti.nextplayer

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.LoopMode
import dev.anilbeesetti.nextplayer.core.model.Resume
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import dev.anilbeesetti.nextplayer.feature.player.utils.PlayerApi
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class PlaylistCompletionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun repeatAllAdvancesAndWrapsExplicitPlaylistWithAutoplayOff() = withPlaylist(LoopMode.ALL) { activity, player, uris ->
        onMain { player.seekTo(59_500) }
        await("second selected item") { player.currentMediaItem?.mediaId == uris[1].toString() && player.isPlaying }
        onMain { player.seekTo(59_500) }
        await("playlist wrapping to first item") { player.currentMediaItem?.mediaId == uris[0].toString() && player.isPlaying }
        onMain {
            assertEquals(2, player.mediaItemCount)
            assertFalse(activity.isFinishing)
        }
    }

    @Test
    fun repeatOffClosesOnlyAfterLastExplicitPlaylistItemWithAutoplayOff() = withPlaylist(LoopMode.OFF) { activity, player, uris ->
        onMain { player.seekTo(59_500) }
        await("second selected item") { player.currentMediaItem?.mediaId == uris[1].toString() && player.isPlaying }
        onMain { assertFalse(activity.isFinishing) }
        onMain { player.seekTo(59_500) }
        await("activity closing after entire playlist") { activity.isDestroyed }
    }

    @Test
    fun repeatOneStillRepeatsCurrentExplicitPlaylistItemWithAutoplayOff() = withPlaylist(LoopMode.ONE) { activity, player, uris ->
        var repeated = false
        onMain {
            player.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) repeated = true
                }
            })
            player.seekTo(59_500)
        }
        await("current item repeating") { repeated && player.isPlaying }
        onMain {
            assertEquals(uris[0].toString(), player.currentMediaItem?.mediaId)
            assertFalse(activity.isFinishing)
        }
    }

    @Test
    fun replacingExplicitPlaylistWithAutomaticQueueRestoresAutoplayOff() = withPlaylist(LoopMode.OFF) { activity, player, uris ->
        onMain {
            // Opening a single folder video supplies an unmarked queue for manual next/previous navigation.
            player.setMediaItems(uris.map { MediaItem.Builder().setUri(it).setMediaId(it.toString()).build() })
            player.prepare()
            player.play()
        }
        await("automatic folder queue playback") { player.isPlaying }
        onMain { player.seekTo(59_500) }
        await("activity closing after first automatic queue item") { activity.isDestroyed }
    }

    private fun withPlaylist(loopMode: LoopMode, check: (PlayerActivity, MediaController, List<Uri>) -> Unit) {
        val context = instrumentation.targetContext
        val preferences = GlobalContext.get().get<PreferencesRepository>()
        val originalPreferences = preferences.playerPreferences.value
        runBlocking {
            preferences.updatePlayerPreferences {
                it.copy(autoplay = false, loopMode = loopMode, resume = Resume.NO, autoPip = false, autoBackgroundPlay = false)
            }
        }
        await("playlist preferences") { preferences.playerPreferences.value.let { !it.autoplay && it.loopMode == loopMode } }
        val audioFiles = (1..2).map { index ->
            File(context.cacheDir, "playlist-completion-$index-${System.nanoTime()}.wav").apply { writeSilentAudio(this) }
        }
        val uris = audioFiles.map(Uri::fromFile)
        var activity: PlayerActivity? = null
        var player: MediaController? = null
        try {
            activity = instrumentation.startActivitySync(
                Intent(context, PlayerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .setData(uris[0])
                    .putParcelableArrayListExtra(PlayerApi.API_PLAYLIST, ArrayList(uris)),
            ) as PlayerActivity
            lateinit var future: ListenableFuture<MediaController>
            onMain {
                future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlayerService::class.java))).buildAsync()
            }
            val controller = future.get(30, TimeUnit.SECONDS)
            player = controller
            await("explicit playlist playback") { controller.isPlaying && controller.mediaItemCount == 2 }
            check(activity, controller, uris)
        } finally {
            onMain { activity?.finish() }
            await("activity cleanup") { activity == null || activity.isDestroyed }
            onMain {
                player?.stop()
                player?.clearMediaItems()
                player?.release()
            }
            context.stopService(Intent(context, PlayerService::class.java))
            audioFiles.forEach(File::delete)
            runBlocking { preferences.updatePlayerPreferences { originalPreferences } }
        }
    }

    private fun writeSilentAudio(file: File) {
        val pcm = ByteArray(16_000 * 2 * 60)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(16_000).putInt(32_000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(pcm.size)
        file.outputStream().use {
            it.write(header.array())
            it.write(pcm)
        }
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var satisfied = false
            onMain { satisfied = condition() }
            if (satisfied) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Timed out waiting for $description")
    }
}
