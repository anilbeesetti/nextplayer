package dev.anilbeesetti.nextplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoThumbnailCachePolicyTest {

    @Test
    fun `normal cached thumbnail is accepted`() {
        assertTrue(
            isSafeCachedThumbnail(
                encodedByteCount = 256_000,
                width = 1_920,
                height = 1_080,
            ),
        )
    }

    @Test
    fun `absurd cached inputs are rejected before pixel allocation`() {
        assertFalse(
            isSafeCachedThumbnail(
                encodedByteCount = MAX_CACHED_THUMBNAIL_BYTES + 1,
                width = 512,
                height = 512,
            ),
        )
        assertFalse(
            isSafeCachedThumbnail(
                encodedByteCount = 256_000,
                width = MAX_CACHED_THUMBNAIL_DIMENSION + 1,
                height = 512,
            ),
        )
        assertFalse(
            isSafeCachedThumbnail(
                encodedByteCount = 256_000,
                width = 0,
                height = 512,
            ),
        )
    }

    @Test
    fun `cache decode sample size is a deterministic power of two`() {
        assertEquals(1, calculateThumbnailInSampleSize(512, 288, 512, 512))
        assertEquals(4, calculateThumbnailInSampleSize(4_096, 2_160, 512, 512))
        assertEquals(8, calculateThumbnailInSampleSize(8_192, 4_320, 512, 512))
    }
}
