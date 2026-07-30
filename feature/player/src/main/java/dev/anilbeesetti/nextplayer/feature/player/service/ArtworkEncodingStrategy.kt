package dev.anilbeesetti.nextplayer.feature.player.service

import kotlin.math.max
import kotlin.math.min

internal const val MAX_PUBLISHED_ARTWORK_BYTES = 256 * 1024
internal const val MAX_PUBLISHED_ARTWORK_DIMENSION = 512

private const val MIN_PUBLISHED_ARTWORK_DIMENSION = 96
private val ARTWORK_ENCODING_QUALITIES = intArrayOf(90, 75, 60, 45)

internal const val MAX_ARTWORK_ENCODING_ATTEMPTS = 28

internal data class ArtworkEncodingAttempt(
    val maxDimension: Int,
    val quality: Int,
)

/**
 * Tries a finite sequence of progressively smaller/lower-quality encodings.
 *
 * The encoder callback must encode the normalized artwork represented by the supplied attempt.
 */
internal fun encodeArtworkWithinLimits(
    sourceWidth: Int,
    sourceHeight: Int,
    encode: (ArtworkEncodingAttempt) -> ByteArray?,
): ByteArray? {
    if (sourceWidth <= 0 || sourceHeight <= 0) return null

    var dimension = min(max(sourceWidth, sourceHeight), MAX_PUBLISHED_ARTWORK_DIMENSION)
    var attempts = 0
    while (attempts < MAX_ARTWORK_ENCODING_ATTEMPTS) {
        for (quality in ARTWORK_ENCODING_QUALITIES) {
            val encoded = encode(ArtworkEncodingAttempt(dimension, quality))
            attempts++
            if (encoded != null && encoded.size <= MAX_PUBLISHED_ARTWORK_BYTES) return encoded
        }

        if (dimension <= MIN_PUBLISHED_ARTWORK_DIMENSION) return null
        dimension = max(MIN_PUBLISHED_ARTWORK_DIMENSION, dimension * 3 / 4)
    }
    return null
}
