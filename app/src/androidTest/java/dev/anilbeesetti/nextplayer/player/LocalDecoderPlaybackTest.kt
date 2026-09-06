package dev.anilbeesetti.nextplayer.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt in with a local clip to exercise Next Player's actual service and controls. */
@RunWith(AndroidJUnit4::class)
class LocalDecoderPlaybackTest {
    @Test
    fun switchSeekAndResume() {
        val args = InstrumentationRegistry.getArguments()
        val clip = args.getString("clip")
        assumeTrue("Pass -e clip /path/to/video", clip != null)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val field = PlayerActivity::class.java.getDeclaredField("mediaController").apply { isAccessible = true }
        val intent = Intent(context, PlayerActivity::class.java).setData(Uri.fromFile(File(clip!!)))
        ActivityScenario.launch<PlayerActivity>(intent).use { scenario ->
            fun await(check: (MediaController) -> Boolean) {
                val deadline = SystemClock.elapsedRealtime() + 15_000
                while (SystemClock.elapsedRealtime() < deadline) {
                    var ready = false
                    scenario.onActivity { activity ->
                        (field.get(activity) as? MediaController)?.let { ready = check(it) }
                    }
                    if (ready) return
                    SystemClock.sleep(25)
                }
                throw AssertionError("Playback condition timed out")
            }
            fun command(mode: String) {
                scenario.onActivity { activity ->
                    (field.get(activity) as MediaController).sendCustomCommand(
                        SessionCommand("SET_VIDEO_DECODER_MODE", Bundle.EMPTY),
                        Bundle().apply { putString("video_decoder_mode", mode) },
                    )
                }
                await { it.sessionExtras.getString("video_decoder_mode") == mode && it.playerError == null }
            }
            await { it.isPlaying && it.duration > 0 }
            command("FFMPEG")
            await { it.isPlaying && it.currentPosition > 500 }
            scenario.onActivity { activity ->
                (field.get(activity) as MediaController).apply {
                    pause()
                    seekTo(2_000)
                }
            }
            await { !it.isPlaying && kotlin.math.abs(it.currentPosition - 2_000) < 100 }
            for (mode in listOf("HARDWARE", "FFMPEG", "SOFTWARE", "FFMPEG")) {
                command(mode)
                await { !it.isPlaying && kotlin.math.abs(it.currentPosition - 2_000) < 100 }
                scenario.onActivity { activity ->
                    val controller = field.get(activity) as MediaController
                    android.util.Log.i("NextlibPlayback", "mode=$mode pausedPositionMs=${controller.currentPosition}")
                }
            }
            SystemClock.sleep(500)
            val dir = File(context.getExternalFilesDir(null), "ffmpeg-benchmark").apply { mkdirs() }
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            File(dir, args.getString("label", "playback") + ".png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.recycle()
            scenario.onActivity { activity -> (field.get(activity) as MediaController).play() }
            await { it.isPlaying && it.currentPosition > 3_000 && it.playerError == null }
            scenario.onActivity { activity ->
                val controller = field.get(activity) as MediaController
                android.util.Log.i("NextlibPlayback", "Resumed at ${controller.currentPosition} ms")
                controller.pause()
                // PlayerActivity updates its intent; ActivityScenario matches lifecycle events by intent.
                activity.intent = Intent(intent)
                activity.finish()
            }
            instrumentation.waitForIdleSync()
        }
    }
}
