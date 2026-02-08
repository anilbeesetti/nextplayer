package dev.anilbeesetti.nextplayer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build.VERSION.SDK_INT
import androidx.core.graphics.drawable.toDrawable
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
import okio.FileSystem
import androidx.core.graphics.get
import io.github.anilbeesetti.nextlib.mediainfo.MediaInfoBuilder
import kotlin.math.abs

class VideoThumbnailDecoder(
    private val source: ImageSource,
    private val options: Options,
    private val strategy: ThumbnailStrategy,
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

            // First, try to get embedded picture (album art/metadata thumbnail)
            val embeddedPicture = retriever.embeddedPicture?.let { pictureBytes ->
                BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size)
            }

            // If embedded picture exists, use it directly
            val rawBitmap = if (embeddedPicture != null) {
                embeddedPicture
            } else {
                // No embedded picture - apply the selected strategy
                val videoDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L

                when (strategy) {
                    is ThumbnailStrategy.FirstFrame -> {
                        retriever.getFrameAtTime(0)
                    }
                    is ThumbnailStrategy.FrameAtPercentage -> {
                        retriever.getFrameAtTime((videoDuration * strategy.percentage * 1000).toLong())
                    }
                    is ThumbnailStrategy.Hybrid -> {
                        val firstFrame = retriever.getFrameAtTime(0)
                        if (firstFrame != null && isSolidColor(firstFrame)) {
                            retriever.getFrameAtTime((videoDuration * strategy.percentage * 1000).toLong())
                        } else {
                            firstFrame
                        }
                    }
                }
            } ?: getThumbnailFromMediaInfo()
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

    private fun getThumbnailFromMediaInfo(): Bitmap? {
        return try {
            // Create a new MediaInfoBuilder instance properly
            val source = this.source
            val metadata = source.metadata
            val mediaInfo = when {
                metadata is ContentMetadata -> {
                    MediaInfoBuilder().from(
                        context = options.context,
                        uri = metadata.uri.toAndroidUri(),
                    ).build()
                }
                source.fileSystem === FileSystem.SYSTEM -> {
                    MediaInfoBuilder().from(
                        filePath = source.file().toFile().path,
                    ).build()
                }
                else -> null
            }
            mediaInfo?.getFrame()?.also { mediaInfo.release() }
        } catch (e: Exception) {
            null
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
                inBitmap.compress(Bitmap.CompressFormat.JPEG, 40, output)
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

    class Factory(
        private val thumbnailStrategy: () -> ThumbnailStrategy,
    ) : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            if (!isApplicable(result.mimeType)) return null
            return VideoThumbnailDecoder(
                source = result.source,
                options = options,
                strategy = thumbnailStrategy(),
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

/**
 * Sealed class representing different thumbnail generation strategies
 */
sealed class ThumbnailStrategy {
    data object FirstFrame : ThumbnailStrategy()
    data class FrameAtPercentage(val percentage: Float = 0.33f) : ThumbnailStrategy()
    data class Hybrid(val percentage: Float = 0.33f) : ThumbnailStrategy()
}

/**
 * Checks if a bitmap is mostly a solid color.
 * Uses a sampling approach to check a grid of pixels in the center region.
 * Returns true if 95% or more of sampled pixels are similar (within threshold).
 */
private fun isSolidColor(bitmap: Bitmap, threshold: Float = 0.7f): Boolean {
    val width = bitmap.width
    val height = bitmap.height

    // Sample a grid in the center region (avoiding edges which may be black bars)
    val marginX = width / 10
    val marginY = height / 10
    val sampleAreaRight = width - marginX
    val sampleAreaBottom = height - marginY

    // Create a grid of sample points
    val gridSize = 10
    val stepX = (sampleAreaRight - marginX) / gridSize
    val stepY = (sampleAreaBottom - marginY) / gridSize

    if (stepX <= 0 || stepY <= 0) return false

    val sampledColors = mutableListOf<Int>()

    for (x in 0 until gridSize) {
        for (y in 0 until gridSize) {
            val pixelX = marginX + x * stepX
            val pixelY = marginY + y * stepY
            if (pixelX < width && pixelY < height) {
                sampledColors.add(bitmap[pixelX, pixelY])
            }
        }
    }

    if (sampledColors.isEmpty()) return false

    // Use the first color as reference
    val referenceColor = sampledColors[0]
    val referenceR = (referenceColor shr 16) and 0xFF
    val referenceG = (referenceColor shr 8) and 0xFF
    val referenceB = referenceColor and 0xFF

    // Count similar colors (within a tolerance)
    val tolerance = 30 // RGB tolerance
    val similarCount = sampledColors.count { color ->
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF

        abs(r - referenceR) <= tolerance &&
                abs(g - referenceG) <= tolerance &&
                abs(b - referenceB) <= tolerance
    }

    val similarityRatio = similarCount.toFloat() / sampledColors.size
    return similarityRatio >= threshold
}
