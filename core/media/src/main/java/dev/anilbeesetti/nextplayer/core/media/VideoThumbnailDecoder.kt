package dev.anilbeesetti.nextplayer.core.media

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build.VERSION.SDK_INT
import androidx.core.graphics.drawable.toDrawable
import coil3.Bitmap
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.decode.ContentMetadata
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.toAndroidUri
import io.github.anilbeesetti.nextlib.mediainfo.MediaInfoBuilder
import okio.FileSystem

class VideoThumbnailDecoder(
    private val source: ImageSource,
    private val options: Options,
    private val diskCache: Lazy<DiskCache?>,
) : Decoder {

    private val diskCacheKey: String
        get() = options.diskCacheKey ?: run {
            val metadata = source.metadata
            when {
                metadata is ContentMetadata -> metadata.uri.toAndroidUri().toString()
                source.fileSystem === FileSystem.SYSTEM -> source.file().toFile().path
                else -> error("Not supported")
            }
        }

    @OptIn(ExperimentalCoilApi::class)
    override suspend fun decode(): DecodeResult {
        readFromDiskCache()?.use { snapshot ->
            val cachedBitmap = snapshot.data.toFile().inputStream().use { input ->
                BitmapFactory.decodeStream(input)
            }

            if (cachedBitmap != null) {
                return DecodeResult(
                    image = cachedBitmap.toDrawable(options.context.resources).asImage(),
                    isSampled = false,
                )
            }
        }

        return MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(source)

            val rawBitmap = retriever.embeddedPicture?.let { embeddedPicture ->
                BitmapFactory.decodeByteArray(
                    embeddedPicture,
                    0,
                    embeddedPicture.size,
                )
            } ?: retriever.getFrameAtTime(0)
                ?: getThumbnailFromMediaInfo()
                ?: throw IllegalStateException("Failed to get video thumbnail.")

            val bitmap = writeToDiskCache(rawBitmap)

            DecodeResult(
                image = bitmap.toDrawable(options.context.resources).asImage(),
                isSampled = false,
            )
        }
    }

    private fun MediaMetadataRetriever.setDataSource(source: ImageSource) {
        val metadata = source.metadata
        when {
            metadata is ContentMetadata -> {
                setDataSource(options.context, metadata.uri.toAndroidUri())
            }

            source.fileSystem === FileSystem.SYSTEM -> {
                setDataSource(source.file().toFile().path)
            }

            else -> error("Not supported")
        }
    }

    private fun MediaInfoBuilder.setDataSource(source: ImageSource): MediaInfoBuilder {
        val metadata = source.metadata
        return when {
            metadata is ContentMetadata -> {
                from(context = options.context, uri = metadata.uri.toAndroidUri())
            }

            source.fileSystem === FileSystem.SYSTEM -> {
                from(filePath = source.file().toFile().path)
            }

            else -> error("Not supported")
        }
    }

    private fun getThumbnailFromMediaInfo(): Bitmap? {
        val mediaInfo = runCatching { MediaInfoBuilder().setDataSource(source).build() }.getOrNull() ?: return null
        return try {
            mediaInfo.getFrame()
        } finally {
            mediaInfo.release()
        }
    }

    private fun readFromDiskCache(): DiskCache.Snapshot? {
        return if (options.diskCachePolicy.readEnabled) {
            diskCache.value?.openSnapshot(diskCacheKey)
        } else {
            null
        }
    }

    private fun writeToDiskCache(inBitmap: Bitmap): Bitmap {
        if (!options.diskCachePolicy.writeEnabled) return inBitmap
        val editor = diskCache.value?.openEditor(diskCacheKey) ?: return inBitmap
        try {
            editor.data.toFile().outputStream().use { output ->
                inBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 40, output)
            }
            editor.commitAndOpenSnapshot()?.use { snapshot ->
                val outBitmap = snapshot.data.toFile().inputStream().use { input ->
                    BitmapFactory.decodeStream(input)
                }
                inBitmap.recycle()
                return outBitmap
            }
        } catch (_: Exception) {
            try {
                editor.abort()
            } catch (_: Exception) {
            }
        }
        return inBitmap
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            if (!isApplicable(result.mimeType)) return null
            return VideoThumbnailDecoder(
                source = result.source,
                options = options,
                diskCache = lazy { imageLoader.diskCache },
            )
        }

        private fun isApplicable(mimeType: String?): Boolean {
            return mimeType != null && mimeType.startsWith("video/")
        }
    }
}

private inline fun <T> MediaMetadataRetriever.use(block: (MediaMetadataRetriever) -> T): T {
    try {
        return block(this)
    } finally {
        // We must call 'close' on API 29+ to avoid a strict mode warning.
        if (SDK_INT >= 29) {
            close()
        } else {
            release()
        }
    }
}
