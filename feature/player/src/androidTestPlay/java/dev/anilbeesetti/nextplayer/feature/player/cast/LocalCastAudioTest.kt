package dev.anilbeesetti.nextplayer.feature.player.cast

import android.media.MediaFormat
import androidx.core.net.toUri
import androidx.media3.inspector.MediaExtractorCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalCastAudioTest {
    @Test
    fun japaneseRemuxPreservesCompressedVideoAndAudioAndOriginalFile() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // Allows checking a reported file without shipping copyrighted media in the test assets.
        val supplied = InstrumentationRegistry.getArguments().getString("castAudioSource")
        val input = supplied?.let {
            File(
                context.cacheDir,
                it,
            )
        }
            ?: File.createTempFile(
                "cast-audio-test-",
                ".mkv",
                context.cacheDir,
            )
        if (supplied == null) {
            instrumentation.context.assets.open("cast-multi-audio.mkv").use { source ->
                input.outputStream().use(source::copyTo)
            }
        }
        val audio = LocalCastAudio(context)
        try {
            val originalHash = fileHash(input)
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
                fileHash(input),
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
            assertEquals(
                listOf(output),
                output.parentFile?.listFiles()?.toList(),
            )
            audio.select(
                input.toUri(),
                0,
                null,
            )
            assertNull(audio.sourceFor(input.toUri()))
            assertEquals(
                0,
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

    private fun fileHash(file: File): ByteArray {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                hash.update(
                    buffer,
                    0,
                    count,
                )
            }
        }
        return hash.digest()
    }

    private fun samples(file: File, type: String, index: Int = 0): ByteArray {
        val extractor =
            MediaExtractorCompat(InstrumentationRegistry.getInstrumentation().targetContext)
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
