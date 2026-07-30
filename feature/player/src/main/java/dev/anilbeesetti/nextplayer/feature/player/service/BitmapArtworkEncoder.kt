package dev.anilbeesetti.nextplayer.feature.player.service

import android.graphics.Bitmap
import androidx.core.graphics.scale
import coil3.Image
import coil3.toBitmap
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.roundToInt

internal fun encodePublishedArtwork(image: Image): ByteArray? {
    val sourceWidth = image.width
    val sourceHeight = image.height
    if (sourceWidth <= 0 || sourceHeight <= 0) return null

    val sourceMaxDimension = max(sourceWidth, sourceHeight)
    val initialScale = (MAX_PUBLISHED_ARTWORK_DIMENSION.toFloat() / sourceMaxDimension).coerceAtMost(1f)
    val initialWidth = (sourceWidth * initialScale).roundToInt().coerceAtLeast(1)
    val initialHeight = (sourceHeight * initialScale).roundToInt().coerceAtLeast(1)
    val normalizedBitmap = image.toBitmap(initialWidth, initialHeight)

    var attemptBitmap: Bitmap = normalizedBitmap
    var attemptDimension = max(initialWidth, initialHeight)
    return try {
        encodeArtworkWithinLimits(initialWidth, initialHeight) { attempt ->
            if (attempt.maxDimension != attemptDimension) {
                if (attemptBitmap !== normalizedBitmap) attemptBitmap.recycle()
                attemptBitmap = normalizedBitmap.scaleDownTo(attempt.maxDimension)
                attemptDimension = attempt.maxDimension
            }
            attemptBitmap.encodeJpeg(attempt.quality)
        }
    } finally {
        if (attemptBitmap !== normalizedBitmap) attemptBitmap.recycle()
    }
}

private fun Bitmap.scaleDownTo(maxDimension: Int): Bitmap {
    val currentMaxDimension = max(width, height)
    if (currentMaxDimension <= maxDimension) return this
    val scale = maxDimension.toFloat() / currentMaxDimension
    val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
    return scale(targetWidth, targetHeight, true)
}

private fun Bitmap.encodeJpeg(quality: Int): ByteArray? {
    val output = BoundedByteArrayOutputStream(MAX_PUBLISHED_ARTWORK_BYTES)
    if (!compress(Bitmap.CompressFormat.JPEG, quality, output)) return null
    return output.toByteArrayOrNull()
}

/** Keeps pathological encodes from growing an unbounded intermediate byte array. */
private class BoundedByteArrayOutputStream(
    private val maxBytes: Int,
) : OutputStream() {
    private val buffer = ByteArray(maxBytes)
    private var count = 0
    private var overflowed = false

    override fun write(value: Int) {
        if (count < maxBytes) {
            buffer[count++] = value.toByte()
        } else {
            overflowed = true
        }
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        val available = maxBytes - count
        val copyLength = length.coerceAtMost(available)
        if (copyLength > 0) {
            bytes.copyInto(buffer, destinationOffset = count, startIndex = offset, endIndex = offset + copyLength)
            count += copyLength
        }
        if (copyLength < length) overflowed = true
    }

    fun toByteArrayOrNull(): ByteArray? = if (overflowed) null else buffer.copyOf(count)
}
