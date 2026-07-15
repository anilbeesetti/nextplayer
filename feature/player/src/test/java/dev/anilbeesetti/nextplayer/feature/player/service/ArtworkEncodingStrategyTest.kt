package dev.anilbeesetti.nextplayer.feature.player.service

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkEncodingStrategyTest {

    @Test
    fun `oversized artwork is reduced until its encoded payload fits`() {
        val attemptedEncodings = mutableListOf<ArtworkEncodingAttempt>()
        val expected = ByteArray(MAX_PUBLISHED_ARTWORK_BYTES) { 7 }

        val result = encodeArtworkWithinLimits(
            sourceWidth = 4_096,
            sourceHeight = 2_048,
        ) { attempt ->
            attemptedEncodings += attempt
            if (attempt.maxDimension <= 288 && attempt.quality <= 60) {
                expected
            } else {
                ByteArray(MAX_PUBLISHED_ARTWORK_BYTES + 1)
            }
        }

        assertNotNull(result)
        assertArrayEquals(expected, result)
        assertTrue(result!!.size <= MAX_PUBLISHED_ARTWORK_BYTES)
        assertEquals(MAX_PUBLISHED_ARTWORK_DIMENSION, attemptedEncodings.first().maxDimension)
        assertTrue(attemptedEncodings.all { it.maxDimension <= MAX_PUBLISHED_ARTWORK_DIMENSION })
        assertTrue(attemptedEncodings.size <= MAX_ARTWORK_ENCODING_ATTEMPTS)
    }

    @Test
    fun `normal artwork uses the highest quality bounded attempt`() {
        val attemptedEncodings = mutableListOf<ArtworkEncodingAttempt>()
        val expected = byteArrayOf(1, 2, 3, 4)

        val result = encodeArtworkWithinLimits(
            sourceWidth = 320,
            sourceHeight = 180,
        ) { attempt ->
            attemptedEncodings += attempt
            expected
        }

        assertArrayEquals(expected, result)
        assertEquals(
            listOf(ArtworkEncodingAttempt(maxDimension = 320, quality = 90)),
            attemptedEncodings,
        )
    }

    @Test
    fun `encoding terminates with no artwork when every bounded attempt is oversized`() {
        var attemptCount = 0

        val result = encodeArtworkWithinLimits(
            sourceWidth = Int.MAX_VALUE,
            sourceHeight = Int.MAX_VALUE,
        ) {
            attemptCount++
            ByteArray(MAX_PUBLISHED_ARTWORK_BYTES + 1)
        }

        assertNull(result)
        assertEquals(MAX_ARTWORK_ENCODING_ATTEMPTS, attemptCount)
    }

    @Test
    fun `invalid image dimensions are rejected without encoding`() {
        var attemptedEncoding = false

        val result = encodeArtworkWithinLimits(sourceWidth = 0, sourceHeight = 512) {
            attemptedEncoding = true
            byteArrayOf(1)
        }

        assertNull(result)
        assertEquals(false, attemptedEncoding)
    }
}
