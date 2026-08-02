package dev.anilbeesetti.nextplayer.core.domain

import java.io.IOException
import dev.anilbeesetti.nextplayer.core.model.Video
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
            fetchVideos = {
                listOf(syncVideo("content://one"))
            },
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
            fetchVideos = {
                fetched = true
                emptyList()
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
            fetchVideos = { throw IOException("MediaStore unavailable") },
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
            fetchVideos = { emptyList() },
            removeMissingVideos = { cleaned = true },
        )

        assertFalse(result)
        assertFalse(cleaned)
    }
}

private fun syncVideo(uri: String) = Video(
    id = 1,
    path = "/Movies/One.mp4",
    parentPath = "/Movies",
    duration = 1_000,
    uriString = uri,
    nameWithExtension = "One.mp4",
    width = 1920,
    height = 1080,
    size = 1_000,
)
