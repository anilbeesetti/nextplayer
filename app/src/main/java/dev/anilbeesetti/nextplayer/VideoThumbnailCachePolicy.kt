package dev.anilbeesetti.nextplayer

internal const val MAX_CACHED_THUMBNAIL_BYTES = 8L * 1024 * 1024
internal const val MAX_CACHED_THUMBNAIL_DIMENSION = 16_384

private const val MAX_CACHED_THUMBNAIL_PIXELS = 100_000_000L

internal fun isSafeCachedThumbnail(
    encodedByteCount: Long,
    width: Int,
    height: Int,
): Boolean = encodedByteCount in 1..MAX_CACHED_THUMBNAIL_BYTES &&
    width in 1..MAX_CACHED_THUMBNAIL_DIMENSION &&
    height in 1..MAX_CACHED_THUMBNAIL_DIMENSION &&
    width.toLong() * height <= MAX_CACHED_THUMBNAIL_PIXELS

internal fun calculateThumbnailInSampleSize(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int,
): Int {
    if (targetWidth <= 0 || targetHeight <= 0) return 1
    var sampleSize = 1
    while (
        sourceWidth / (sampleSize * 2) >= targetWidth &&
        sourceHeight / (sampleSize * 2) >= targetHeight
    ) {
        sampleSize *= 2
    }
    return sampleSize
}
