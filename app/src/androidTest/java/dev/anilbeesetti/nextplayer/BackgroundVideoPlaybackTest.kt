package dev.anilbeesetti.nextplayer

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.media3.common.C
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.LoopMode
import dev.anilbeesetti.nextplayer.core.model.Resume
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderRecoveryStatus
import dev.anilbeesetti.nextplayer.feature.player.service.CustomCommands
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import dev.anilbeesetti.nextplayer.feature.player.service.setVideoDecoderMode
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class BackgroundVideoPlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun softwareVideoResumesAfterScreenOffWhileAudioContinues() = checkScreenOff(DecoderMode.SOFTWARE)

    @Test
    fun ffmpegVideoResumesAfterScreenOffWhileAudioContinues() = checkScreenOff(DecoderMode.FFMPEG)

    private fun checkScreenOff(mode: DecoderMode) {
        val context = instrumentation.targetContext
        val preferences = GlobalContext.get().get<PreferencesRepository>()
        val originalPreferences = preferences.playerPreferences.value
        val video = File(context.cacheDir, "background-playback.mp4")
        instrumentation.context.assets.open("background-playback.mp4").use { input ->
            video.outputStream().use { input.copyTo(it) }
        }
        var activity: PlayerActivity? = null
        var player: MediaController? = null
        try {
            runBlocking {
                preferences.updatePlayerPreferences {
                    it.copy(autoBackgroundPlay = true, autoPip = false, loopMode = LoopMode.ONE, resume = Resume.NO)
                }
            }
            await("background playback preferences") { preferences.playerPreferences.value.autoBackgroundPlay }
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            val firstActivity = instrumentation.startActivitySync(
                Intent(context, PlayerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .setData(Uri.fromFile(video)),
            ) as PlayerActivity
            activity = firstActivity
            lateinit var future: ListenableFuture<MediaController>
            onMain {
                future = MediaController.Builder(
                    context,
                    SessionToken(context, ComponentName(context, PlayerService::class.java)),
                ).buildAsync()
            }
            val controller = future.get(60, TimeUnit.SECONDS)
            player = controller
            await("initial playback") { controller.isPlaying }
            runBlocking {
                withContext(Dispatchers.Main) { assertTrue(controller.setVideoDecoderMode(mode)) }
            }
            await("requested video decoder") { controller.isPlaying && controller.sessionExtras.getString(CustomCommands.VIDEO_DECODER_MODE_KEY) == mode.name }

            repeat(2) {
                shell("input keyevent KEYCODE_SLEEP")
                await("background audio without a video renderer") {
                    firstActivity.lifecycle.currentState == Lifecycle.State.CREATED &&
                        C.TRACK_TYPE_VIDEO in controller.trackSelectionParameters.disabledTrackTypes &&
                        !controller.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO) &&
                        controller.currentTracks.isTypeSelected(C.TRACK_TYPE_AUDIO) && controller.isPlaying
                }
                var position = 0L
                onMain { position = controller.currentPosition }
                await("audio playback advancing with the screen off") { controller.currentPosition >= position + 500 || controller.currentPosition < position }

                shell("input keyevent KEYCODE_WAKEUP")
                shell("wm dismiss-keyguard")
                await("video decoder restored after screen wake") {
                    firstActivity.lifecycle.currentState == Lifecycle.State.RESUMED &&
                        C.TRACK_TYPE_VIDEO !in controller.trackSelectionParameters.disabledTrackTypes &&
                        controller.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO) &&
                        controller.sessionExtras.getString(CustomCommands.VIDEO_DECODER_MODE_KEY) == mode.name && controller.isPlaying
                }
                onMain {
                    assertNull(controller.playerError)
                    assertEquals(DecoderRecoveryStatus.NONE.name, controller.sessionExtras.getString(CustomCommands.DECODER_RECOVERY_STATUS_KEY))
                    assertEquals(video.toURI().path, controller.currentMediaItem?.localConfiguration?.uri?.path)
                }
            }
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
            video.delete()
            runBlocking { preferences.updatePlayerPreferences { originalPreferences } }
        }
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 60_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var satisfied = false
            onMain { satisfied = condition() }
            if (satisfied) return
            SystemClock.sleep(100)
        }
        throw AssertionError("Timed out waiting for $description")
    }
}
