package dev.anilbeesetti.nextplayer.core.media.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreQueryRunnerTest {

    @Test
    fun `returns empty list when MediaStore denies access`() {
        val result = runMediaStoreQuery<String> {
            throw SecurityException("Permission denial")
        }

        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns successful query result unchanged`() {
        val result = runMediaStoreQuery { listOf("video") }

        assertEquals(listOf("video"), result)
    }

    @Test(expected = IllegalStateException::class)
    fun `propagates exceptions unrelated to permission`() {
        runMediaStoreQuery<String> {
            throw IllegalStateException("Provider failure")
        }
    }
}
