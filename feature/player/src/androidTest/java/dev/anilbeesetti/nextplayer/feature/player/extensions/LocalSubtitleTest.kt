package dev.anilbeesetti.nextplayer.feature.player.extensions

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalSubtitleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun addedSubtitlesBecomeTracksAndRenderWithoutReopening() {
        val context = instrumentation.targetContext
        val directory = File(context.cacheDir, "local-subtitle-test").apply { mkdirs() }
        val audio = File(directory, "silence.wav")
        val pcm = ByteArray(16_000 * 2 * 60)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(16_000).putInt(32_000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(pcm.size)
        audio.outputStream().use {
            it.write(header.array())
            it.write(pcm)
        }
        lateinit var player: ExoPlayer
        onMain {
            player = ExoPlayer.Builder(context).build()
            player.setMediaItems(
                listOf("before", "current", "after").map {
                    MediaItem.Builder().setMediaId(it).setUri(Uri.fromFile(audio)).build()
                },
                1,
                5_000,
            )
            player.prepare()
        }
        try {
            await("initial playback ready") { player.playbackState == Player.STATE_READY }
            for ((index, playing) in listOf(false, true).withIndex()) {
                val text = "Local subtitle $index"
                val subtitleFile = File(directory, "$index.srt").apply {
                    writeText("1\n00:00:00,000 --> 00:01:00,000\n$text\n")
                }
                val subtitle = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(subtitleFile))
                    .setId("subtitle-$index")
                    .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                    .build()
                onMain {
                    player.playWhenReady = playing
                    player.addAdditionalSubtitleConfiguration(subtitle)
                    assertEquals(1, player.currentMediaItemIndex)
                    assertEquals("current", player.currentMediaItem?.mediaId)
                    assertEquals(playing, player.playWhenReady)
                }
                await("subtitle $index available immediately") {
                    assertNull(player.playerError)
                    player.currentTracks.groups.count { it.type == C.TRACK_TYPE_TEXT } == index + 1
                }
                onMain { player.switchTrack(C.TRACK_TYPE_TEXT, index) }
                await("subtitle $index rendered") {
                    player.currentCues.cues.any { it.text?.toString() == text }
                }
                onMain {
                    assertEquals(playing, player.playWhenReady)
                    if (playing) {
                        assertTrue(player.currentPosition >= 5_000)
                    } else {
                        assertEquals(5_000L, player.currentPosition)
                    }
                }
            }
        } finally {
            onMain { player.release() }
            directory.deleteRecursively()
        }
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

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
