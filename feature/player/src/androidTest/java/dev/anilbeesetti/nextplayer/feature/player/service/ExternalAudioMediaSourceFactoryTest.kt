package dev.anilbeesetti.nextplayer.feature.player.service

import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.anilbeesetti.nextplayer.feature.player.extensions.switchTrack
import dev.anilbeesetti.nextplayer.feature.player.extensions.withExternalAudio
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class ExternalAudioMediaSourceFactoryTest {
    @Test
    fun mergesSwitchableAudioWithoutShorteningPrimaryTimeline() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val primary = wav(File(context.cacheDir, "primary.wav"), 3)
        val shortAudio = wav(File(context.cacheDir, "short.wav"), 1)
        val longAudio = wav(File(context.cacheDir, "long.wav"), 5)
        lateinit var player: ExoPlayer
        try {
            instrumentation.runOnMainSync {
                player = ExoPlayer.Builder(context)
                    .setMediaSourceFactory(ExternalAudioMediaSourceFactory(DefaultMediaSourceFactory(context)))
                    .build()
                player.setMediaItem(
                    MediaItem.fromUri(primary.toUri()).withExternalAudio(listOf(shortAudio.toUri(), longAudio.toUri())),
                )
                player.prepare()
            }
            await { player.playbackState == Player.STATE_READY }
            instrumentation.runOnMainSync {
                assertEquals(3000L, player.duration)
                assertEquals(3, player.currentTracks.groups.count { it.type == C.TRACK_TYPE_AUDIO })
                player.switchTrack(C.TRACK_TYPE_AUDIO, 1)
                player.setPlaybackSpeed(1.5f)
                player.seekTo(2000)
            }
            await { player.currentTracks.groups[1].isSelected && player.playbackState == Player.STATE_READY }
            instrumentation.runOnMainSync {
                assertEquals(2000L, player.currentPosition)
                assertEquals(1.5f, player.playbackParameters.speed)
                assertTrue(!player.playWhenReady)
                player.switchTrack(C.TRACK_TYPE_AUDIO, 0)
            }
            await { player.currentTracks.groups[0].isSelected }
            instrumentation.runOnMainSync { player.switchTrack(C.TRACK_TYPE_AUDIO, -1) }
            await { player.currentTracks.groups.none { it.isSelected } }
        } finally {
            instrumentation.runOnMainSync { player.release() }
            listOf(primary, shortAudio, longAudio).forEach { it.delete() }
        }
    }

    private fun await(condition: () -> Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        repeat(100) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            Thread.sleep(50)
        }
        throw AssertionError("Playback condition did not become true")
    }

    private fun wav(file: File, seconds: Int): File {
        val bytes = seconds * 8000 * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + bytes).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(bytes).array()
        file.outputStream().use {
            it.write(header)
            it.write(ByteArray(bytes))
        }
        return file
    }
}
