package dev.anilbeesetti.nextplayer.feature.player.cast

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Only the Cast copy changes; the original URI and its tracks remain available on the phone. */
@UnstableApi
internal class LocalCastAudio(private val context: Context) {
    data class Track(val sourceIndex: Int, val format: Format, val isDefault: Boolean)
    private val copies = mutableMapOf<Pair<Uri, Int>, File>()
    private val selections = mutableMapOf<Uri, Int>()
    private val directory = File(
        context.cacheDir,
        "cast-audio-${java.util.UUID.randomUUID()}",
    )

    fun sourceFor(uri: Uri): Uri? = selections[uri]?.let { copies[uri to it]?.toUri() }
    fun selectedIndex(uri: Uri, tracks: List<Track>): Int = selections[uri]
        ?: tracks.indexOfFirst { it.isDefault }.takeIf { it >= 0 } ?: 0

    fun select(uri: Uri, index: Int, file: File) {
        copies[uri to index] = file
        selections[uri] = index
    }

    fun cached(uri: Uri, index: Int): File? = copies[uri to index]?.takeIf { it.isFile }

    fun inspect(uri: Uri): List<Track> = withExtractor(uri) { extractor ->
        (0 until extractor.trackCount).mapNotNull { index ->
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@mapNotNull null
            if (!mime.startsWith("audio/")) return@mapNotNull null
            Track(
                index,
                Format.Builder()
                    .setId("cast-local-audio:$uri:$index")
                    .setSampleMimeType(mime)
                    .setLanguage(format.getString(MediaFormat.KEY_LANGUAGE))
                    .build(),
                format.containsKey(MediaFormat.KEY_IS_DEFAULT) &&
                    format.getInteger(MediaFormat.KEY_IS_DEFAULT) != 0,
            )
        }
    }

    /** Copy compressed packets, not decoded frames: no quality loss or video encoding load. */
    suspend fun remux(uri: Uri, audioTrack: Track): File {
        check(directory.isDirectory || directory.mkdirs())
        val output = File.createTempFile(
            "audio-",
            ".mp4",
            directory,
        )
        try {
            withExtractor(uri) { extractor ->
                val videoIndex = (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(
                        it,
                    ).getString(MediaFormat.KEY_MIME)?.startsWith("video/") ==
                        true
                } ?: error("No video track")
                val muxer = MediaMuxer(
                    output.path,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
                )
                try {
                    val sources = listOf(
                        videoIndex,
                        audioTrack.sourceIndex,
                    )
                    val tracks = sources.associateWith { source ->
                        extractor.selectTrack(source)
                        val format = extractor.getTrackFormat(source)
                        if (source == videoIndex && format.containsKey(MediaFormat.KEY_ROTATION)) {
                            muxer.setOrientationHint(format.getInteger(MediaFormat.KEY_ROTATION))
                        }
                        muxer.addTrack(format)
                    }
                    var buffer = ByteBuffer.allocateDirect(DEFAULT_BUFFER_SIZE)
                    val info = MediaCodec.BufferInfo()
                    muxer.start()
                    while (extractor.sampleTrackIndex >= 0) {
                        currentCoroutineContext().ensureActive()
                        if (Build.VERSION.SDK_INT >= 28 &&
                            extractor.sampleSize > buffer.capacity()
                        ) {
                            require(extractor.sampleSize <= MAX_SAMPLE_SIZE) {
                                "Oversized media sample"
                            }
                            buffer = ByteBuffer.allocateDirect(extractor.sampleSize.toInt())
                        }
                        require(
                            extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0,
                        ) {
                            "Encrypted media cannot be remuxed"
                        }
                        buffer.clear()
                        val size = extractor.readSampleData(
                            buffer,
                            0,
                        )
                        if (size < 0) break
                        info.set(
                            0,
                            size,
                            extractor.sampleTime,
                            extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC,
                        )
                        muxer.writeSampleData(
                            checkNotNull(tracks[extractor.sampleTrackIndex]),
                            buffer,
                            info,
                        )
                        extractor.advance()
                    }
                    muxer.stop()
                } finally {
                    muxer.release()
                }
            }
            return output
        } catch (error: Throwable) {
            output.delete()
            throw error
        }
    }

    fun clear() {
        selections.clear()
        copies.clear()
        directory.deleteRecursively()
    }

    private inline fun <T> withExtractor(uri: Uri, block: (MediaExtractor) -> T): T {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(
                context,
                uri,
                null,
            )
            return block(extractor)
        } finally {
            extractor.release()
        }
    }
}

private const val DEFAULT_BUFFER_SIZE = 4 * 1024 * 1024
private const val MAX_SAMPLE_SIZE = 16 * 1024 * 1024L
