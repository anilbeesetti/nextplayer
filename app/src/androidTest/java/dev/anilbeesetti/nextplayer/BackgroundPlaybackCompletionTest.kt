package dev.anilbeesetti.nextplayer

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.LoopMode
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import dev.anilbeesetti.nextplayer.feature.player.utils.PlayerApi
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class BackgroundPlaybackCompletionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun wakingAfterBackgroundCompletionDoesNotReplayClearedQueue() = checkCompletion(autoplay = false)

    @Test
    fun wakingAfterBackgroundCompletionClosesEndedQueue() = checkCompletion(autoplay = true)

    private fun checkCompletion(autoplay: Boolean) {
        val context = instrumentation.targetContext
        val preferences = GlobalContext.get().get<PreferencesRepository>()
        val originalPreferences = preferences.playerPreferences.value
        runBlocking {
            preferences.updatePlayerPreferences {
                it.copy(autoBackgroundPlay = true, autoPip = false, autoplay = autoplay, loopMode = LoopMode.OFF)
            }
        }
        await("background playback preferences") {
            preferences.playerPreferences.value.let { it.autoBackgroundPlay && !it.autoPip && it.autoplay == autoplay }
        }

        val audio = File(context.cacheDir, "background-completion-${System.nanoTime()}.wav")
        val pcm = ByteArray(16_000 * 2 * 60)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(16_000).putInt(32_000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(pcm.size)
        audio.outputStream().use {
            it.write(header.array())
            it.write(pcm)
        }
        val intent = Intent(context, PlayerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .setData(Uri.fromFile(audio))
            .putExtra(PlayerApi.API_POSITION, 45_000)
        var activity: PlayerActivity? = null
        var player: MediaController? = null
        try {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            val firstActivity = instrumentation.startActivitySync(intent) as PlayerActivity
            activity = firstActivity
            lateinit var future: ListenableFuture<MediaController>
            onMain {
                future = MediaController.Builder(
                    context,
                    SessionToken(context, ComponentName(context, PlayerService::class.java)),
                ).buildAsync()
            }
            val controller = future.get(10, TimeUnit.SECONDS)
            player = controller
            await("resumed playback") { controller.isPlaying && controller.currentPosition >= 45_000 }

            shell("input keyevent KEYCODE_SLEEP")
            await("screen off") { firstActivity.lifecycle.currentState == Lifecycle.State.CREATED }
            onMain {
                assertTrue(controller.isPlaying)
                controller.seekTo(59_500)
            }
            await("background completion") {
                if (autoplay) controller.playbackState == Player.STATE_ENDED else controller.mediaItemCount == 0
            }
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            await("completed player activity to close without replaying") { firstActivity.isDestroyed }

            // A deliberate new request for the same media must still start playback.
            activity = instrumentation.startActivitySync(intent) as PlayerActivity
            await("explicit replay") { controller.isPlaying && controller.currentPosition >= 45_000 }
        } finally {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            onMain { activity?.finish() }
            await("activity cleanup") { activity == null || activity.isDestroyed }
            onMain {
                player?.stop()
                player?.clearMediaItems()
                player?.release()
            }
            context.stopService(Intent(context, PlayerService::class.java))
            audio.delete()
            runBlocking { preferences.updatePlayerPreferences { originalPreferences } }
        }
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var satisfied = false
            onMain { satisfied = condition() }
            if (satisfied) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Timed out waiting for $description")
    }
}
