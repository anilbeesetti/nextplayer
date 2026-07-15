package dev.anilbeesetti.nextplayer.feature.player.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistStartIndexResolverTest {
    @Test
    fun secondPlaybackReadsCurrentApiPlaylistAndSelectedIndex() {
        var currentData = PlayerApiData(
            hasPosition = true,
            position = 100,
            hasTitle = true,
            title = "Playlist A",
            playlist = listOf("https://example.test/a-1.mp4", "https://example.test/a-2.mp4"),
        )
        val api = PlayerApi { currentData }

        assertEquals(
            listOf("https://example.test/a-1.mp4", "https://example.test/a-2.mp4"),
            api.getPlaylist(),
        )

        currentData = PlayerApiData(
            hasPosition = true,
            position = 200,
            hasTitle = true,
            title = "Playlist B",
            shouldReturnResult = true,
            playlist = listOf("https://example.test/b-1.mp4", "https://example.test/b-2.mp4"),
        )
        val secondPlaylist = api.getPlaylist()

        assertEquals(
            listOf("https://example.test/b-1.mp4", "https://example.test/b-2.mp4"),
            secondPlaylist,
        )
        assertEquals(200, api.position)
        assertEquals("Playlist B", api.title)
        assertEquals(true, api.shouldReturnResult)
        assertEquals(
            1,
            resolvePlaylistStartIndex(
                playlist = secondPlaylist,
                originalSelectedUri = "https://example.test/b-2.mp4",
                normalizedSelectedUri = null,
            ),
        )
    }

    @Test
    fun originalSelectedUriWinsWhenItsNormalizedFormAppearsEarlier() {
        val original = "file:///storage/emulated/0/Movies/second.mp4"
        val normalized = "content://media/external/video/media/42"
        val playlist = listOf(normalized, "content://media/external/video/media/7", original)

        assertEquals(
            2,
            resolvePlaylistStartIndex(
                playlist = playlist,
                originalSelectedUri = original,
                normalizedSelectedUri = normalized,
            ),
        )
    }

    @Test
    fun normalizedSelectedUriIsUsedWhenOriginalIsAbsent() {
        val playlist = listOf(
            "content://media/external/video/media/7",
            "content://media/external/video/media/42",
        )

        assertEquals(
            1,
            resolvePlaylistStartIndex(
                playlist = playlist,
                originalSelectedUri = "content://com.android.providers.media.documents/document/video%3A42",
                normalizedSelectedUri = "content://media/external/video/media/42",
            ),
        )
    }

    @Test
    fun ordinaryExactUriSelectsItsPlaylistPosition() {
        assertEquals(
            1,
            resolvePlaylistStartIndex(
                playlist = listOf("https://example.test/first.mp4", "https://example.test/second.mp4"),
                originalSelectedUri = "https://example.test/second.mp4",
                normalizedSelectedUri = null,
            ),
        )
    }

    @Test
    fun missingUriFallsBackToFirstPlaylistItem() {
        assertEquals(
            0,
            resolvePlaylistStartIndex(
                playlist = listOf("content://video/first", "content://video/second"),
                originalSelectedUri = "content://video/missing",
                normalizedSelectedUri = null,
            ),
        )
    }
}
