package io.github.anilbeesetti.nextlib.media3ext.ffextractor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.os.ParcelFileDescriptor
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegLibrary
import java.io.File
import java.io.IOException
import java.util.Locale

@UnstableApi
class FfmpegAsfExtractor(
    private val context: Context,
    private val uri: Uri?,
    private val responseHeaders: Map<String, List<String>>,
) : Extractor {

    private var extractorOutput: ExtractorOutput? = null

    private var nativeHandle: Long = 0L
    private var openedPfd: ParcelFileDescriptor? = null
    private var trackOutputs: List<TrackOutput> = emptyList()

    private var isInitialized = false
    private var pendingSeekTimeUs: Long? = null
    private var durationUs: Long = C.TIME_UNSET

    override fun sniff(input: ExtractorInput): Boolean {
        val uri = uri ?: return false

        val contentTypes = responseHeaders
            .entries
            .firstOrNull { it.key.equals("content-type", ignoreCase = true) }
            ?.value
            ?.joinToString(separator = ",")
            ?.lowercase(Locale.US)
            .orEmpty()

        val filename = context.getDisplayName(uri).orEmpty()
        val lowerName = filename.lowercase(Locale.US)
        val lowerPath = (uri.lastPathSegment ?: "").lowercase(Locale.US)

        val looksLikeAsfContainer =
            lowerName.endsWith(".wmv") ||
                lowerName.endsWith(".asf") ||
                lowerName.endsWith(".wma") ||
                lowerPath.endsWith(".wmv") ||
                lowerPath.endsWith(".asf") ||
                lowerPath.endsWith(".wma") ||
                contentTypes.contains(WmvMimeTypes.VIDEO_X_MS_WMV) ||
                contentTypes.contains(WmvMimeTypes.VIDEO_X_MS_ASF) ||
                contentTypes.contains(WmvMimeTypes.APPLICATION_VND_MS_ASF)

        return looksLikeAsfContainer
    }

    override fun init(output: ExtractorOutput) {
        extractorOutput = output
    }

    override fun seek(position: Long, timeUs: Long) {
        pendingSeekTimeUs = timeUs
    }

    override fun release() {
        runCatching {
            if (nativeHandle != 0L) {
                nativeRelease(nativeHandle)
                nativeHandle = 0L
            }
        }
        runCatching {
            openedPfd?.close()
            openedPfd = null
        }
        isInitialized = false
        trackOutputs = emptyList()
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        val output = extractorOutput ?: return Extractor.RESULT_END_OF_INPUT
        val uri = uri ?: return Extractor.RESULT_END_OF_INPUT

        if (!isInitialized) {
            if (!FfmpegLibrary.isAvailable()) {
                throw IllegalStateException("FFmpeg native library is not available")
            }
            val handle = openNative(uri)
            nativeHandle = handle

            durationUs = nativeGetDurationUs(handle)
            val trackCount = nativeGetTrackCount(handle)
            if (trackCount <= 0) {
                throw IOException("No supported tracks in ASF/WMV: $uri")
            }

            trackOutputs = (0 until trackCount).map { trackIndex ->
                val info = nativeGetTrackInfo(handle, trackIndex)
                val trackOutput = output.track(trackIndex, info.trackType)
                trackOutput.format(info.toMedia3Format())
                trackOutput
            }

            output.endTracks()
            output.seekMap(AsfSeekMap(durationUs = durationUs))
            isInitialized = true
        }

        pendingSeekTimeUs?.let { timeUs ->
            nativeSeekToUs(nativeHandle, timeUs)
            pendingSeekTimeUs = null
        }

        val packet = nativeReadPacket(nativeHandle) ?: return Extractor.RESULT_END_OF_INPUT
        val data = packet.data ?: return Extractor.RESULT_END_OF_INPUT

        val trackIndex = packet.trackIndex
        if (trackIndex !in trackOutputs.indices) return Extractor.RESULT_CONTINUE

        val trackOutput = trackOutputs[trackIndex]
        val parsable = ParsableByteArray(data)
        trackOutput.sampleData(parsable, data.size)
        trackOutput.sampleMetadata(
            packet.timeUs,
            packet.flags,
            data.size,
            /* offset= */ 0,
            /* cryptoData= */ null,
        )
        return Extractor.RESULT_CONTINUE
    }

    private fun openNative(uri: Uri): Long {
        val scheme = uri.scheme?.lowercase(Locale.US)

        if (scheme == "http" || scheme == "https") {
            val handle = nativeOpenFromUrl(uri.toString())
            if (handle == 0L) throw IOException("FFmpeg failed to open URL: $uri")
            return handle
        }

        if (scheme == "file") {
            val path = uri.path
            if (!path.isNullOrBlank()) {
                val file = File(path)
                if (file.exists()) {
                    val handle = nativeOpenFromPath(file.absolutePath)
                    if (handle == 0L) throw IOException("FFmpeg failed to open file: $uri")
                    return handle
                }
            }
        }

        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("Unable to open content URI: $uri")
        openedPfd = pfd
        val handle = nativeOpenFromFd(pfd.fd)
        if (handle == 0L) throw IOException("FFmpeg failed to open file descriptor: $uri")
        return handle
    }

    private fun Context.getDisplayName(uri: Uri): String? {
        val last = uri.lastPathSegment
        if (uri.scheme?.equals("content", ignoreCase = true) != true) return last
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        return runCatching {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        }.getOrNull() ?: last
    }

    private fun FfmpegTrackInfo.toMedia3Format(): Format {
        val builder = Format.Builder()
            .setSampleMimeType(sampleMimeType)
            .setCodecs(codecs)
            .setLanguage(language)
            .setAverageBitrate(averageBitrate.takeIf { it > 0 } ?: Format.NO_VALUE)

        when (trackType) {
            C.TRACK_TYPE_VIDEO -> builder
                .setWidth(width.takeIf { it > 0 } ?: Format.NO_VALUE)
                .setHeight(height.takeIf { it > 0 } ?: Format.NO_VALUE)
                .setRotationDegrees(rotationDegrees.takeIf { it != 0 } ?: 0)

            C.TRACK_TYPE_AUDIO -> builder
                .setChannelCount(channelCount.takeIf { it > 0 } ?: Format.NO_VALUE)
                .setSampleRate(sampleRate.takeIf { it > 0 } ?: Format.NO_VALUE)
        }

        if (extraData != null && extraData.isNotEmpty()) {
            builder.setInitializationData(listOf(extraData))
        }

        return builder.build()
    }

    private class AsfSeekMap(
        private val durationUs: Long,
    ) : SeekMap {
        override fun isSeekable(): Boolean = durationUs != C.TIME_UNSET
        override fun getDurationUs(): Long = durationUs
        override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints =
            SeekMap.SeekPoints(SeekPoint(timeUs, /* position= */ 0))
    }

    private external fun nativeOpenFromUrl(url: String): Long

    private external fun nativeOpenFromPath(path: String): Long

    private external fun nativeOpenFromFd(fd: Int): Long

    private external fun nativeGetTrackCount(handle: Long): Int

    private external fun nativeGetDurationUs(handle: Long): Long

    private external fun nativeGetTrackInfo(handle: Long, trackIndex: Int): FfmpegTrackInfo

    private external fun nativeReadPacket(handle: Long): FfmpegPacket?

    private external fun nativeSeekToUs(handle: Long, timeUs: Long)

    private external fun nativeRelease(handle: Long)

    companion object {
        init {
            // Ensure libmedia3ext is loaded.
            FfmpegLibrary.isAvailable()
        }
    }
}
