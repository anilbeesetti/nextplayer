package dev.anilbeesetti.nextplayer

import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.SystemClock
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderManager
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegMediaSourceFactory
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompatibilityPlaybackTest {
    @Test
    fun programStreamWithHeaderRendersSeeksAndSwitchesDecoders() {
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
        val renderedTimeUs = AtomicLong(Long.MIN_VALUE)
        val decoderName = AtomicReference<String>()
        val error = AtomicReference<String>()
        lateinit var player: ExoPlayer
        lateinit var texture: SurfaceTexture
        lateinit var surface: Surface
        val decoders = DecoderManager(initialVideoMode = DecoderMode.SYSTEM)
        fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
        fun awaitVideo(targetMs: Long) {
            val deadline = SystemClock.elapsedRealtime() + 20_000
            var ready = false
            while (error.get() == null && SystemClock.elapsedRealtime() < deadline) {
                onMain { ready = player.playbackState == Player.STATE_READY }
                if (ready && renderedTimeUs.get() >= targetMs * 1_000) break
                SystemClock.sleep(50)
            }
            assertTrue(
                "No frame at ${targetMs}ms: ${error.get()}, rendered=${renderedTimeUs.get()}",
                ready && renderedTimeUs.get() >= targetMs * 1_000,
            )
        }
        onMain {
            texture = SurfaceTexture(false)
            surface = Surface(texture)
            player = ExoPlayer.Builder(context)
                .setRenderersFactory(NextRenderersFactory(context).setDecoderManager(decoders))
                .build()
            decoders.attach(player)
            player.addListener(object : Player.Listener {
                override fun onPlayerError(exception: PlaybackException) {
                    error.set(exception.stackTraceToString())
                }
            })
            player.addAnalyticsListener(object : AnalyticsListener {
                override fun onVideoDecoderInitialized(
                    eventTime: AnalyticsListener.EventTime,
                    name: String,
                    initializedTimestampMs: Long,
                    initializationDurationMs: Long,
                ) {
                    decoderName.set(name)
                }
            })
            player.setVideoFrameMetadataListener { timeUs, _, _, _ -> renderedTimeUs.set(timeUs) }
            player.setVideoSurface(surface)
            player.setMediaSource(
                FfmpegMediaSourceFactory(
                    DefaultDataSource.Factory(context),
                ).createMediaSource(MediaItem.fromUri(uri)),
            )
            player.prepare()
            player.play()
        }
        try {
            awaitVideo(0)
            assertTrue(
                "Expected system decoder: ${decoderName.get()}",
                !decoderName.get().startsWith("ffmpeg"),
            )
            val forwardMs = if (suppliedPath == null) 1_500L else 120_000L
            for (position in listOf(
                forwardMs,
                200L,
                forwardMs,
            )) {
                onMain {
                    player.pause()
                    renderedTimeUs.set(Long.MIN_VALUE)
                    player.seekTo(position)
                    player.play()
                }
                awaitVideo(position)
            }
            fun awaitRenderedOutput() {
                val outputDeadline = SystemClock.elapsedRealtime() + 10_000
                var count = 0
                while (count == 0 && error.get() == null &&
                    SystemClock.elapsedRealtime() < outputDeadline
                ) {
                    onMain {
                        player.videoDecoderCounters?.let { counters ->
                            counters.ensureUpdated()
                            count =
                                counters.renderedOutputBufferCount
                        }
                    }
                    SystemClock.sleep(50)
                }
                assertTrue(
                    "Decoder initialized without rendering: ${decoderName.get()}, ${error.get()}",
                    count > 0,
                )
            }
            onMain {
                player.seekTo(0)
                decoders.selectVideoDecoder(DecoderMode.FFMPEG)
            }
            val deadline = SystemClock.elapsedRealtime() + 15_000
            while (!decoderName.get().startsWith("ffmpeg") && error.get() == null &&
                SystemClock.elapsedRealtime() < deadline
            ) {
                SystemClock.sleep(50)
            }
            assertTrue(
                "FFmpeg switch: ${error.get()}, decoder=${decoderName.get()}",
                decoderName.get().startsWith("ffmpeg"),
            )
            awaitRenderedOutput()
            onMain { decoders.selectVideoDecoder(DecoderMode.SYSTEM) }
            val systemDeadline = SystemClock.elapsedRealtime() + 15_000
            while (decoderName.get().startsWith("ffmpeg") && error.get() == null &&
                SystemClock.elapsedRealtime() < systemDeadline
            ) {
                SystemClock.sleep(50)
            }
            assertTrue(
                "System switch: ${error.get()}",
                !decoderName.get().startsWith("ffmpeg"),
            )
            onMain {
                assertEquals(
                    Player.STATE_READY,
                    player.playbackState,
                )
            }
        } finally {
            onMain {
                decoders.detach()
                player.release()
                surface.release()
                texture.release()
            }
            fixture.delete()
        }
    }
}
