package dev.anilbeesetti.nextplayer

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import dev.anilbeesetti.nextplayer.feature.player.extensions.audioDecoderMode
import dev.anilbeesetti.nextplayer.feature.player.extensions.switchTrack
import dev.anilbeesetti.nextplayer.feature.player.extensions.videoDecoderMode
import dev.anilbeesetti.nextplayer.feature.player.service.CustomCommands
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalSubtitleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun subtitlesAndDecoderChoicesSurviveReloadsAndPlaylistNavigation() {
        val context = instrumentation.targetContext
        val directory = File(context.cacheDir, "local-subtitle-test-${System.nanoTime()}").apply { mkdirs() }

        val pcm = ByteArray(16_000 * 2 * 60)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(16_000).putInt(32_000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(pcm.size)
        val items = listOf("before", "current", "after").map { name ->
            val audio = File(directory, "$name.wav")
            audio.outputStream().use {
                it.write(header.array())
                it.write(pcm)
            }
            val uri = Uri.fromFile(audio)
            MediaItem.Builder().setMediaId(uri.toString()).setUri(uri).build()
        }
        lateinit var future: ListenableFuture<MediaController>
        onMain {
            future = MediaController.Builder(
                context,
                SessionToken(context, ComponentName(context, PlayerService::class.java)),
            ).buildAsync()
        }
        val player = future.get(10, TimeUnit.SECONDS)
        onMain {
            player.setMediaItems(items, 1, 5_000)
            player.prepare()
        }
        try {
            await("initial playback ready") { player.playbackState == Player.STATE_READY }
            selectDecoder(player, CustomCommands.SET_VIDEO_DECODER_MODE, CustomCommands.VIDEO_DECODER_MODE_KEY, DecoderMode.HARDWARE)
            selectDecoder(player, CustomCommands.SET_AUDIO_DECODER_MODE, CustomCommands.AUDIO_DECODER_MODE_KEY, DecoderMode.SOFTWARE)
            for ((index, playing) in listOf(false, true).withIndex()) {
                val text = "Local subtitle $index"
                val subtitleFile = File(directory, "$index.srt").apply {
                    writeText("1\n00:00:00,000 --> 00:01:00,000\n$text\n")
                }
                onMain { player.playWhenReady = playing }
                command(
                    player,
                    CustomCommands.ADD_SUBTITLE_TRACK,
                    Bundle().apply {
                        putString(CustomCommands.SUBTITLE_TRACK_URI_KEY, Uri.fromFile(subtitleFile).toString())
                    },
                )
                await("subtitle $index available immediately") {
                    assertNull(player.playerError)
                    player.currentTracks.groups.count { it.type == C.TRACK_TYPE_TEXT } == index + 1
                }
                onMain { player.switchTrack(C.TRACK_TYPE_TEXT, index) }
                await("subtitle $index rendered") {
                    player.currentCues.cues.any { it.text?.toString() == text }
                }
                onMain {
                    assertEquals(1, player.currentMediaItemIndex)
                    assertEquals(items.map { it.mediaId }, (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId })
                    assertEquals(DecoderMode.HARDWARE, player.currentMediaItem?.mediaMetadata?.videoDecoderMode)
                    assertEquals(DecoderMode.SOFTWARE, player.currentMediaItem?.mediaMetadata?.audioDecoderMode)
                    assertEquals(playing, player.playWhenReady)
                    if (playing) {
                        assertTrue(player.currentPosition >= 5_000)
                    } else {
                        assertEquals(5_000L, player.currentPosition)
                    }
                }
            }
            onMain {
                player.pause()
                player.seekTo(2, 1_000)
            }
            await("new item defaults to Auto") {
                player.currentMediaItem?.mediaId == items[2].mediaId &&
                    (player.currentMediaItem?.mediaMetadata?.videoDecoderMode ?: DecoderMode.AUTO) == DecoderMode.AUTO &&
                    (player.currentMediaItem?.mediaMetadata?.audioDecoderMode ?: DecoderMode.AUTO) == DecoderMode.AUTO &&
                    player.playbackState == Player.STATE_READY
            }
            onMain { player.seekTo(1, 5_000) }
            await("returning to item restores both choices") {
                player.currentMediaItem?.mediaId == items[1].mediaId &&
                    player.currentMediaItem?.mediaMetadata?.videoDecoderMode == DecoderMode.HARDWARE &&
                    player.currentMediaItem?.mediaMetadata?.audioDecoderMode == DecoderMode.SOFTWARE &&
                    player.playbackState == Player.STATE_READY
            }
        } finally {
            onMain {
                player.stop()
                player.clearMediaItems()
                player.release()
            }
            context.stopService(Intent(context, PlayerService::class.java))
            directory.deleteRecursively()
        }
    }

    private fun selectDecoder(player: MediaController, customCommand: CustomCommands, key: String, mode: DecoderMode) {
        command(player, customCommand, Bundle().apply { putString(key, mode.name) })
    }

    private fun command(player: MediaController, command: CustomCommands, args: Bundle) {
        lateinit var result: ListenableFuture<SessionResult>
        onMain { result = player.sendCustomCommand(command.sessionCommand, args) }
        assertEquals(SessionResult.RESULT_SUCCESS, result.get(10, TimeUnit.SECONDS).resultCode)
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
