package dev.anilbeesetti.nextplayer.feature.player.cast

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalCastAudioTest {
    @Test
    fun japaneseHeaderPreservesMediaBytesAndCreatesNoCachedMovie() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // Check reported files without shipping copyrighted media in the test assets.
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
        var output: File? = null
        try {
            val originalHash = fileHash(input)
            val existingFiles = context.cacheDir.listFiles().orEmpty().map { it.name }.toSet()
            val tracks = audio.inspect(input.toUri())
            assertEquals(
                listOf(
                    "en",
                    "ja",
                    "en",
                ),
                tracks.map { it.format.language },
            )
            val patch = audio.prepare(
                input.toUri(),
                tracks[1],
            )
            audio.select(
                input.toUri(),
                1,
                patch,
            )
            assertEquals(
                patch,
                audio.patchFor(input.toUri()),
            )
            assertEquals(
                1,
                audio.selectedIndex(
                    input.toUri(),
                    tracks,
                ),
            )
            assertEquals(
                existingFiles,
                context.cacheDir.listFiles().orEmpty().map {
                    it.name
                }.toSet(),
            )
            // Only this test materializes the virtual stream, for Android demuxer validation.
            output = File.createTempFile(
                "cast-header-test-",
                ".mkv",
                context.cacheDir,
            )
            patch.wrap(input.inputStream()).use { stream ->
                output.outputStream().use(stream::copyTo)
            }
            assertEquals(
                input.length(),
                output.length(),
            )
            assertEquals(
                listOf(
                    "ja",
                    "en",
                    "en",
                ),
                audio.inspect(output.toUri()).map {
                    it.format.language
                },
            )
            assertArrayEquals(
                fileHash(
                    input,
                    patch,
                ),
                fileHash(
                    output,
                    patch,
                ),
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
                    japanese = true,
                ),
                samples(
                    output,
                    "audio/",
                    japanese = true,
                ),
            )
            assertArrayEquals(
                originalHash,
                fileHash(input),
            )
            audio.clear()
            assertNull(audio.patchFor(input.toUri()))
        } finally {
            audio.clear()
            output?.delete()
            input.delete()
        }
    }

    @Test
    fun nonMatroskaInputDoesNotAdvertiseFallbackTracksOrCreateFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audio = LocalCastAudio(context)
        val input = File.createTempFile(
            "invalid-cast-audio-",
            ".mp4",
            context.cacheDir,
        )
        try {
            input.writeText("not a Matroska file")
            val existingFiles = context.cacheDir.listFiles().orEmpty().map { it.name }.toSet()
            assertEquals(
                emptyList<LocalCastAudio.Track>(),
                audio.inspect(input.toUri()),
            )
            assertEquals(
                existingFiles,
                context.cacheDir.listFiles().orEmpty().map {
                    it.name
                }.toSet(),
            )
            assertEquals(
                "not a Matroska file",
                input.readText(),
            )
        } finally {
            audio.clear()
            input.delete()
        }
    }

    private fun fileHash(file: File, excluded: MatroskaTrackPatch? = null): ByteArray {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var position = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (excluded == null) {
                    hash.update(
                        buffer,
                        0,
                        count,
                    )
                } else {
                    val prefix = (excluded.offset - position).coerceIn(
                        0,
                        count.toLong(),
                    ).toInt()
                    hash.update(
                        buffer,
                        0,
                        prefix,
                    )
                    val suffix = (excluded.offset + excluded.length - position).coerceIn(
                        0,
                        count.toLong(),
                    ).toInt()
                    hash.update(
                        buffer,
                        suffix,
                        count - suffix,
                    )
                }
                position += count
            }
        }
        return hash.digest()
    }

    private fun samples(file: File, type: String, japanese: Boolean = false): ByteArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).first {
                val format = extractor.getTrackFormat(it)
                format.getString(MediaFormat.KEY_MIME)?.startsWith(type) == true &&
                    (
                        !japanese || format.getString(MediaFormat.KEY_LANGUAGE) in listOf(
                            "jpn",
                            "ja",
                        )
                        )
            }
            extractor.selectTrack(track)
            val hash = MessageDigest.getInstance("SHA-256")
            val buffer = ByteBuffer.allocate(1024 * 1024)
            while (extractor.sampleTime in 0 until 2_000_000L) {
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
