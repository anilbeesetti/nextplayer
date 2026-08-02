package dev.anilbeesetti.nextplayer.core.domain

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPlaylistsWithMediaUseCaseTest {

    @Test
    fun successfulFetchRemovesOnlyUrisMissingFromSnapshot() = runTest {
        var receivedUris: Set<String>? = null

        val result = synchronizePlaylistsWithMedia(
            hasStoragePermission = { true },
            fetchVideoUris = { setOf("content://one") },
            removeMissingVideos = { receivedUris = it },
        )

        assertTrue(result)
        assertEquals(setOf("content://one"), receivedUris)
    }

    @Test
    fun missingPermissionNeverRunsFetchOrCleanup() = runTest {
        var fetched = false
        var cleaned = false

        val result = synchronizePlaylistsWithMedia(
            hasStoragePermission = { false },
            fetchVideoUris = {
                fetched = true
                emptySet()
            },
            removeMissingVideos = { cleaned = true },
        )

        assertFalse(result)
        assertFalse(fetched)
        assertFalse(cleaned)
    }

    @Test
    fun failedFetchNeverRunsCleanup() = runTest {
        var cleaned = false

        val result = synchronizePlaylistsWithMedia(
            hasStoragePermission = { true },
            fetchVideoUris = { throw IOException("MediaStore unavailable") },
            removeMissingVideos = { cleaned = true },
        )

        assertFalse(result)
        assertFalse(cleaned)
    }

    @Test
    fun permissionRevokedDuringFetchNeverRunsCleanup() = runTest {
        var permissionChecks = 0
        var cleaned = false

        val result = synchronizePlaylistsWithMedia(
            hasStoragePermission = { permissionChecks++ == 0 },
            fetchVideoUris = { emptySet() },
            removeMissingVideos = { cleaned = true },
        )

        assertFalse(result)
        assertFalse(cleaned)
    }
}
