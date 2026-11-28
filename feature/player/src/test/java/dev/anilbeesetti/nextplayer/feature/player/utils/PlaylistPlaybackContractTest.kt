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
        val queue = playlistRecord().toMediaQueue(selectedUri = "https://example.com/two")

        assertEquals(
            listOf(
                "https://example.com/one",
                "https://example.com/two",
            ),
            queue?.mediaItems?.map { it.mediaId },
        )
        assertEquals(
            1,
            queue?.startIndex,
        )
    }

    @Test
    fun queueAddsParsedTitleAndArtworkMetadata() {
        val queue = playlistRecord().toMediaQueue(selectedUri = "https://example.com/one")

        val item = queue?.mediaItems?.first()
        assertEquals(
            "One",
            item?.mediaMetadata?.title,
        )
        assertEquals(
            "https://example.com/one.png",
            item?.mediaMetadata?.artworkUri?.toString(),
        )
    }

    @Test
    fun missingSelectedEntryReturnsNullForSingleItemFallback() {
        assertNull(playlistRecord().toMediaQueue(selectedUri = "https://example.com/missing"))
    }

    @Test
    fun queueSelectsTheRepeatedUriByEntryId() {
        val playlist = playlistRecord()
        val first = playlist.items.first()
        val repeated = first.copy(
            id = 3,
            position = 2,
            title = "Three",
        )
        val queue = checkNotNull(
            playlist.copy(items = playlist.items + repeated).toMediaQueue(
                selectedUri = repeated.uri,
                itemId = repeated.id,
            ),
        )

        assertEquals(
            2,
            queue.startIndex,
        )
        assertEquals(
            listOf(
                "One",
                "Two",
                "Three",
            ),
            queue.mediaItems.map { it.mediaMetadata.title },
        )
        assertEquals(
            listOf(
                1L,
                2L,
                3L,
            ),
            queue.mediaItems.map { it.playlistQueueKey },
        )
        assertEquals(
            first.uri,
            queue.mediaItems.last().mediaId,
        )
    }

    private fun playlistRecord() = PlaylistRecord(
        id = 7,
        name = "Channels",
        type = PlaylistType.M3U_URL,
        source = "https://example.com/list.m3u",
        items = listOf(
            PlaylistItemRecord(
                id = 1,
                position = 0,
                uri = "https://example.com/one",
                title = "One",
                tvgLogo = "https://example.com/one.png",
            ),
            PlaylistItemRecord(
                id = 2,
                position = 1,
                uri = "https://example.com/two",
                title = "Two",
            ),
        ),
        lastRefreshedAt = 123,
    )
}
