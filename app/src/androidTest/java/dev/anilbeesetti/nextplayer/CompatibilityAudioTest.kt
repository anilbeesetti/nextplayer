package dev.anilbeesetti.nextplayer

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.C
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompatibilityAudioTest {
    @Test
    fun compatibilityReaderKeepsOpusVorbisAndFlacAudio() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val error = AtomicReference<String>()
        val advancingEvents = AtomicInteger()
        lateinit var player: ExoPlayer
        val decoders = DecoderManager(initialAudioMode = DecoderMode.SYSTEM)
        fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
        onMain {
            player = ExoPlayer.Builder(
                context,
            ).setRenderersFactory(
                NextRenderersFactory(context).setDecoderManager(decoders),
            ).build()
            decoders.attach(player)
            player.volume = 0f
            player.addAnalyticsListener(object : AnalyticsListener {
                override fun onAudioPositionAdvancing(
                    eventTime: AnalyticsListener.EventTime,
                    playoutStartSystemTimeMs: Long,
                ) {
                    advancingEvents.incrementAndGet()
                }
            })
            player.addListener(object : Player.Listener {
                override fun onPlayerError(exception: PlaybackException) {
                    error.set(exception.stackTraceToString())
                }
            })
        }
        try {
            for (asset in listOf(
                "compatibility-opus.mkv",
                "compatibility-vorbis.mkv",
                "compatibility.flac",
            )) {
                val file = File(
                    context.cacheDir,
                    asset,
                )
                instrumentation.context.assets.open(asset).use { input ->
                    file.outputStream().use { input.copyTo(it) }
                }
                try {
                    val initialEvents = advancingEvents.get()
                    onMain {
                        error.set(null)
                        player.stop()
                        player.setMediaSource(
                            FfmpegMediaSourceFactory(
                                DefaultDataSource.Factory(context),
                            ).createMediaSource(MediaItem.fromUri(Uri.fromFile(file))),
                        )
                        player.prepare()
                        player.play()
                    }
                    val deadline = SystemClock.elapsedRealtime() + 15_000
                    var rendered = false
                    while (!rendered && error.get() == null &&
                        SystemClock.elapsedRealtime() < deadline
                    ) {
                        onMain {
                            val counters = player.audioDecoderCounters
                            counters?.ensureUpdated()
                            val hasAudio = player.currentTracks.groups.any {
                                it.type == C.TRACK_TYPE_AUDIO && it.isSelected
                            }
                            rendered = hasAudio && (counters?.renderedOutputBufferCount ?: 0) > 0 &&
                                advancingEvents.get() > initialEvents
                        }
                        SystemClock.sleep(50)
                    }
                    assertTrue(
                        "Missing audio for $asset: ${error.get()}",
                        rendered,
                    )
                    if (asset.endsWith(".flac")) {
                        for (target in listOf(
                            1000L,
                            0L,
                        )) {
                            var previousEvents = 0
                            onMain {
                                player.pause()
                                previousEvents = advancingEvents.get()
                                player.seekTo(target)
                                player.play()
                            }
                            val seekDeadline = SystemClock.elapsedRealtime() + 10_000
                            var advanced = false
                            while (!advanced && error.get() == null &&
                                SystemClock.elapsedRealtime() < seekDeadline
                            ) {
                                onMain {
                                    advanced = advancingEvents.get() > previousEvents &&
                                        player.currentPosition >= target + 50
                                }
                                SystemClock.sleep(50)
                            }
                            assertTrue(
                                "FLAC seek to $target: ${error.get()}",
                                advanced,
                            )
                        }
                    }
                } finally {
                    file.delete()
                }
            }
        } finally {
            onMain {
                decoders.detach()
                player.release()
            }
        }
    }
}
