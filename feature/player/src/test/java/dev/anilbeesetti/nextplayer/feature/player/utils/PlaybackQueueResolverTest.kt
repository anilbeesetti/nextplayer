package dev.anilbeesetti.nextplayer.feature.player.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueResolverTest {
    @Test
    fun explicitOneItemPlaylistResolvesWithoutMediaStoreExpansion() = runBlocking {
        var localPlaylistRequested = false
        val selectedUri = "content://media/external/video/media/42"

        val queue = resolvePlaybackQueue(
            selectedUri = selectedUri,
            normalizedSelectedUri = selectedUri,
            explicitPlaylist = listOf(selectedUri),
            getLocalPlaylist = {
                localPlaylistRequested = true
                listOf("content://media/external/video/media/41", selectedUri)
            },
        )

        assertEquals(listOf(selectedUri), queue)
        assertFalse(localPlaylistRequested)
    }

    @Test
    fun ordinaryOneItemMediaLaunchStillExpandsFromMediaStore() = runBlocking {
        var localPlaylistRequested = false
        val selectedUri = "content://com.android.providers.media.documents/document/video%3A42"
        val normalizedSelectedUri = "content://media/external/video/media/42"
        val folderPlaylist = listOf(
            "content://media/external/video/media/41",
            normalizedSelectedUri,
        )

        val queue = resolvePlaybackQueue(
            selectedUri = selectedUri,
            normalizedSelectedUri = normalizedSelectedUri,
            explicitPlaylist = emptyList(),
            getLocalPlaylist = {
                localPlaylistRequested = true
                folderPlaylist
            },
        )

        assertEquals(folderPlaylist, queue)
        assertTrue(localPlaylistRequested)
    }
}
