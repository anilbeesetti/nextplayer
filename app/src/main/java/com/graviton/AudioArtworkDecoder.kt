package com.graviton

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.core.graphics.drawable.toDrawable
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.ContentMetadata
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.toAndroidUri
import okio.FileSystem

/**
 * Extracts embedded album art from audio files so Coil can show the real picture
 * instead of a generic placeholder.
 */
class AudioArtworkDecoder(
    private val source: ImageSource,
    private val options: Options,
) : Decoder {

    override suspend fun decode(): DecodeResult {
        val bytes = MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(source, options)
            retriever.embeddedPicture
        } ?: error("No embedded artwork")
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("Embedded artwork could not be decoded")
        return DecodeResult(
            image = bitmap.toDrawable(options.context.resources).asImage(),
            isSampled = false,
        )
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            if (!isApplicable(result, options)) return null
            return AudioArtworkDecoder(result.source, options)
        }

        private fun isApplicable(result: SourceFetchResult, options: Options): Boolean {
            val mime = result.mimeType.orEmpty()
            if (mime.startsWith("audio/")) return true
            if (mime.startsWith("image/") || mime.startsWith("video/")) return false
            val data = options.data.toString()
            return AUDIO_HINTS.any { data.contains(it, ignoreCase = true) }
        }
    }

    private companion object {
        val AUDIO_HINTS = listOf(
            "/audio/",
            "audio/media",
            ".mp3",
            ".flac",
            ".m4a",
            ".aac",
            ".ogg",
            ".opus",
            ".wav",
            ".wma",
        )
    }
}

private fun MediaMetadataRetriever.setDataSource(source: ImageSource, options: Options) {
    val metadata = source.metadata
    when {
        metadata is ContentMetadata -> setDataSource(options.context, metadata.uri.toAndroidUri())
        source.fileSystem === FileSystem.SYSTEM -> setDataSource(source.file().toFile().path)
        else -> error("Not supported")
    }
}

private inline fun <T> MediaMetadataRetriever.use(block: (MediaMetadataRetriever) -> T): T {
    try {
        return block(this)
    } finally {
        if (Build.VERSION.SDK_INT >= 29) close() else release()
    }
}
