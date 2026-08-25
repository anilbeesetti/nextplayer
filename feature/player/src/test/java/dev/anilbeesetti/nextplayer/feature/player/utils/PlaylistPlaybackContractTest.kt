package dev.anilbeesetti.nextplayer.feature.player.utils

import dev.anilbeesetti.nextplayer.core.model.PlaylistItemRecord
import dev.anilbeesetti.nextplayer.core.model.PlaylistRecord
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PlaylistPlaybackContractTest {

    @Test
    fun queueKeepsDatabaseOrderAndSelectsRequestedUri() {
        val queue = playlistRecord().toMediaQueue("https://example.com/two")

        assertEquals(
            listOf("https://example.com/one", "https://example.com/two"),
            queue?.mediaItems?.map { it.mediaId },
        )
        assertEquals(1, queue?.startIndex)
    }

    @Test
    fun queueAddsParsedTitleAndArtworkMetadata() {
        val queue = playlistRecord().toMediaQueue("https://example.com/one")

        val item = queue?.mediaItems?.first()
        assertEquals("One", item?.mediaMetadata?.title)
        assertEquals(
            "https://example.com/one.png",
            item?.mediaMetadata?.artworkUri?.toString(),
        )
    }

    @Test
    fun missingSelectedEntryReturnsNullForSingleItemFallback() {
        assertNull(playlistRecord().toMediaQueue("https://example.com/missing"))
    }

    private fun playlistRecord() = PlaylistRecord(
        id = 7,
        name = "Channels",
        type = PlaylistType.M3U_URL,
        source = "https://example.com/list.m3u",
        items = listOf(
            PlaylistItemRecord(
                position = 0,
                uri = "https://example.com/one",
                title = "One",
                tvgLogo = "https://example.com/one.png",
            ),
            PlaylistItemRecord(
                position = 1,
                uri = "https://example.com/two",
                title = "Two",
            ),
        ),
        lastRefreshedAt = 123,
    )
}
