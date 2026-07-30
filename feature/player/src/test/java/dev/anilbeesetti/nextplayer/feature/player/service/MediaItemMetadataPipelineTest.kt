package dev.anilbeesetti.nextplayer.feature.player.service

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaItemMetadataPipelineTest {

    @Test
    fun `twenty thousand network items bypass local enrichment and preserve metadata order`() = runBlocking {
        val items = List(20_000) { index ->
            MediaItem.Builder()
                .setMediaId("https://stream.example.com/$index.m3u8")
                .setUri("https://stream.example.com/$index.m3u8")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Channel $index")
                        .setArtworkUri(Uri.parse("https://images.example.com/$index.png"))
                        .build(),
                )
                .build()
        }
        var enrichmentCalls = 0

        val result = enrichLocalMediaItems(items) { item ->
            enrichmentCalls++
            item
        }

        assertEquals(0, enrichmentCalls)
        assertEquals(items.size, result.size)
        result.forEachIndexed { index, item ->
            assertSame(items[index], item)
            assertEquals("Channel $index", item.mediaMetadata.title)
            assertEquals("https://images.example.com/$index.png", item.mediaMetadata.artworkUri.toString())
        }
    }

    @Test
    fun `twenty thousand network items enrich only the selected start item`() = runBlocking {
        val selectedIndex = 12_345
        val items = List(20_000) { index ->
            MediaItem.Builder()
                .setMediaId("https://stream.example.com/$index.m3u8")
                .setUri("https://stream.example.com/$index.m3u8")
                .setMediaMetadata(MediaMetadata.Builder().setTitle("Channel $index").build())
                .build()
        }
        val enrichedIds = mutableListOf<String>()

        val result = enrichLocalMediaItems(items, requiredIndexes = setOf(selectedIndex)) { item ->
            enrichedIds += item.mediaId
            item.buildUpon()
                .setMediaMetadata(item.mediaMetadata.buildUpon().setArtist("restored state").build())
                .build()
        }

        assertEquals(listOf(items[selectedIndex].mediaId), enrichedIds)
        assertEquals("restored state", result[selectedIndex].mediaMetadata.artist)
        assertSame(items[selectedIndex - 1], result[selectedIndex - 1])
        assertSame(items[selectedIndex + 1], result[selectedIndex + 1])
    }

    @Test
    fun `local enrichment uses a bounded worker pool and preserves order`() = runBlocking {
        val items = List(24) { index ->
            MediaItem.Builder()
                .setMediaId("content://media/external/video/media/$index")
                .setUri("content://media/external/video/media/$index")
                .setMediaMetadata(MediaMetadata.Builder().setTitle("Video $index").build())
                .build()
        }
        val active = AtomicInteger()
        val peakActive = AtomicInteger()

        val result = enrichLocalMediaItems(items, maxConcurrentEnrichments = 4) { item ->
            val nowActive = active.incrementAndGet()
            peakActive.updateAndGet { peak -> maxOf(peak, nowActive) }
            delay(5)
            active.decrementAndGet()
            item.buildUpon()
                .setMediaMetadata(item.mediaMetadata.buildUpon().setArtist("enriched").build())
                .build()
        }

        assertTrue("expected concurrent work", peakActive.get() > 1)
        assertTrue("peak ${peakActive.get()} exceeded bound", peakActive.get() <= 4)
        assertEquals(items.map { it.mediaId }, result.map { it.mediaId })
        assertTrue(result.all { it.mediaMetadata.artist == "enriched" })
    }
}
