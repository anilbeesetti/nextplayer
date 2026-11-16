package dev.anilbeesetti.nextplayer

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import dev.anilbeesetti.nextplayer.feature.player.extensions.ffmpegExtraction
import dev.anilbeesetti.nextplayer.feature.player.extensions.videoDecoderMode
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderMode
import dev.anilbeesetti.nextplayer.feature.player.service.CustomCommands
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompatibilityServiceTest {
    @Test
    fun unrecognizedRecordingFallsBackAndRetainsExtractionWhenSwitchingToSoftware() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val suppliedPath = InstrumentationRegistry.getArguments().getString("compatibilityVideo")
        val fixture = File(
            context.cacheDir,
            "header-program-stream.mp4",
        )
        if (suppliedPath == null) {
            instrumentation.context.assets.open("header-program-stream.mp4").use { input ->
                fixture.outputStream().use { input.copyTo(it) }
            }
        }
        val uri = Uri.fromFile(suppliedPath?.let(::File) ?: fixture)
        fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
        run {
            lateinit var future: ListenableFuture<MediaController>
            onMain {
                future = MediaController.Builder(
                    context,
                    SessionToken(
                        context,
                        ComponentName(
                            context,
                            PlayerService::class.java,
                        ),
                    ),
                ).buildAsync()
            }
            val player = future.get(
                10,
                TimeUnit.SECONDS,
            )
            onMain {
                player.setMediaItem(
                    MediaItem.Builder().setUri(uri).setMediaId(uri.toString()).build(),
                )
                player.prepare()
                player.play()
            }
            var observed = ""
            fun await(description: String, condition: () -> Boolean) {
                val deadline = SystemClock.elapsedRealtime() + 20_000
                var done = false
                while (!done && SystemClock.elapsedRealtime() < deadline) {
                    onMain {
                        done = condition()
                        val metadata = player.currentMediaItem?.mediaMetadata
                        observed =
                            "mode=${metadata?.videoDecoderMode}, " +
                            "extraction=${metadata?.ffmpegExtraction}, " +
                            "state=${player.playbackState}, error=${player.playerError}"
                    }
                    if (!done) SystemClock.sleep(50)
                }
                assertTrue(
                    "$description: $observed",
                    done,
                )
            }
            try {
                await("automatic HW+ fallback") {
                    val mode = player.currentMediaItem?.mediaMetadata?.videoDecoderMode
                    mode == DecoderMode.HW_PLUS &&
                        player.playbackState == Player.STATE_READY && player.playerError == null
                }
                onMain {
                    player.pause()
                    player.seekTo(500)
                }
                await("seek while paused") {
                    player.playbackState == Player.STATE_READY &&
                        player.currentPosition == 500L
                }
                lateinit var result: ListenableFuture<SessionResult>
                onMain {
                    result = player.sendCustomCommand(
                        CustomCommands.SET_VIDEO_DECODER_MODE.sessionCommand,
                        Bundle().apply {
                            putString(
                                CustomCommands.VIDEO_DECODER_MODE_KEY,
                                DecoderMode.FFMPEG.name,
                            )
                        },
                    )
                }
                assertEquals(
                    SessionResult.RESULT_SUCCESS,
                    result.get(
                        10,
                        TimeUnit.SECONDS,
                    ).resultCode,
                )
                await("SW retains FFmpeg extraction") {
                    player.sessionExtras.getString(CustomCommands.VIDEO_DECODER_MODE_KEY) ==
                        DecoderMode.FFMPEG.name &&
                        player.currentMediaItem?.mediaMetadata?.ffmpegExtraction == true &&
                        player.playbackState == Player.STATE_READY
                }
                onMain { player.play() }
                await("playback advances after switch") {
                    player.currentPosition >= 800 &&
                        player.playerError == null
                }
            } finally {
                onMain {
                    player.stop()
                    player.clearMediaItems()
                    player.release()
                }
                context.stopService(
                    Intent(
                        context,
                        PlayerService::class.java,
                    ),
                )
                fixture.delete()
            }
        }
    }
}
