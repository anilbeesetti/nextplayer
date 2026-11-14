package dev.anilbeesetti.nextplayer.feature.player.cast

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalCastAudioTest {
    @Test
    fun japaneseRemuxPreservesCompressedVideoAndAudioAndOriginalFile() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val input = File.createTempFile(
            "cast-audio-test-",
            ".mkv",
            context.cacheDir,
        )
        instrumentation.context.assets.open("cast-multi-audio.mkv").use { source ->
            input.outputStream().use(source::copyTo)
        }
        val audio = LocalCastAudio(context)
        try {
            val originalHash = input.readBytes().let {
                MessageDigest.getInstance("SHA-256").digest(it)
            }
            val tracks = audio.inspect(input.toUri())
            assertEquals(
                listOf(
                    "en",
                    "ja",
                    "en",
                ),
                tracks.map { it.format.language },
            )
            val japanese = tracks[1]
            val output = audio.remux(
                input.toUri(),
                japanese,
            )
            assertEquals(
                listOf("ja"),
                audio.inspect(output.toUri()).map { it.format.language },
            )
            assertArrayEquals(
                samples(
                    input,
                    "video/",
                ),
                samples(
                    output,
                    "video/",
                ),
            )
            assertArrayEquals(
                samples(
                    input,
                    "audio/",
                    1,
                ),
                samples(
                    output,
                    "audio/",
                ),
            )
            assertArrayEquals(
                originalHash,
                input.readBytes().let {
                    MessageDigest.getInstance("SHA-256").digest(it)
                },
            )
            audio.select(
                input.toUri(),
                1,
                output,
            )
            assertEquals(
                output.toUri(),
                audio.sourceFor(input.toUri()),
            )
            assertEquals(
                1,
                audio.selectedIndex(
                    input.toUri(),
                    tracks,
                ),
            )
            audio.clear()
            assertFalse(output.exists())
        } finally {
            audio.clear()
            input.delete()
        }
    }

    @Test
    fun invalidInputDoesNotLeavePartialCopies() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audio = LocalCastAudio(context)
        val input = File.createTempFile(
            "invalid-cast-audio-",
            ".mkv",
            context.cacheDir,
        )
        try {
            input.writeText("not a media file")
            val track = LocalCastAudio.Track(
                0,
                androidx.media3.common.Format.Builder().build(),
                true,
            )
            try {
                audio.remux(
                    input.toUri(),
                    track,
                )
                org.junit.Assert.fail("Invalid input must fail")
            } catch (_: Exception) {
                assertEquals(
                    "not a media file",
                    input.readText(),
                )
            }
        } finally {
            audio.clear()
            input.delete()
        }
    }

    private fun samples(file: File, type: String, index: Int = 0): ByteArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).filter {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(type) ==
                    true
            }[index]
            extractor.selectTrack(track)
            val hash = MessageDigest.getInstance("SHA-256")
            val buffer = ByteBuffer.allocate(1024 * 1024)
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(
                    buffer,
                    0,
                )
                if (size < 0) break
                hash.update(
                    buffer.array(),
                    0,
                    size,
                )
                extractor.advance()
            }
            return hash.digest()
        } finally {
            extractor.release()
        }
    }
}
