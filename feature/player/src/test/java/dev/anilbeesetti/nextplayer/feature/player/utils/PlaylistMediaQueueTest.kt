package dev.anilbeesetti.nextplayer.feature.player.utils

import dev.anilbeesetti.nextplayer.core.model.Playlist
import dev.anilbeesetti.nextplayer.core.model.PlaylistItem
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistMediaQueueTest {

    @Test
    fun playlistBuildsOrderedMetadataQueueAtSelectedItem() {
        val playlist = playlist(
            items = listOf(
                PlaylistItem("https://stream/1", "News One", 9, "https://img/1.png"),
                PlaylistItem("https://stream/2", "News Two", 1, "https://img/2.png"),
            ),
        )

        val queue = playlist.toMediaQueue("https://stream/2")!!

        assertEquals(1, queue.startIndex)
        assertEquals(
            listOf("https://stream/1", "https://stream/2"),
            queue.mediaItems.map { it.mediaId },
        )
        assertEquals(listOf("News One", "News Two"), queue.mediaItems.map { it.mediaMetadata.title })
        assertEquals("https://img/2.png", queue.mediaItems[1].mediaMetadata.artworkUri.toString())
    }

    @Test
    fun playlistReturnsNullWhenSelectedItemIsMissing() {
        val playlist = playlist(
            items = listOf(PlaylistItem("https://stream/1", "News One", 0)),
        )

        assertNull(playlist.toMediaQueue("https://stream/missing"))
    }

    @Test
    fun sameSelectedUriFromDifferentPlaylistsHasDifferentIdentity() {
        val selectedUri = "https://stream/1"

        assertNotEquals(
            PlaybackRequestIdentity(playlistId = 42, selectedUriString = selectedUri),
            PlaybackRequestIdentity(playlistId = 84, selectedUriString = selectedUri),
        )
    }

    private fun playlist(items: List<PlaylistItem>) = Playlist(
        id = 42,
        name = "News",
        type = PlaylistType.M3U_URL,
        source = "https://example.com/news.m3u",
        items = items,
        lastRefreshedAt = null,
    )
}
