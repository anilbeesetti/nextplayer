package dev.anilbeesetti.nextplayer.feature.player.service

import android.content.ContentResolver
import androidx.media3.common.MediaItem
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

private const val DEFAULT_MAX_CONCURRENT_ENRICHMENTS = 4

/**
 * Enriches local media and explicitly required queue positions with a fixed-size worker pool. Linked
 * network playlists commonly contain tens of thousands of entries, so only their starting item needs
 * persisted-state enrichment while the remaining populated metadata is returned without launching work.
 */
internal suspend fun enrichLocalMediaItems(
    mediaItems: List<MediaItem>,
    requiredIndexes: Set<Int> = emptySet(),
    maxConcurrentEnrichments: Int = DEFAULT_MAX_CONCURRENT_ENRICHMENTS,
    enrich: suspend (MediaItem) -> MediaItem,
): List<MediaItem> {
    require(maxConcurrentEnrichments > 0)
    val enrichmentIndexes = mediaItems.indices.filter { index ->
        index in requiredIndexes || mediaItems[index].requiresLocalMetadataEnrichment()
    }
    if (enrichmentIndexes.isEmpty()) return mediaItems

    val result = mediaItems.toMutableList()
    val nextEnrichmentIndex = AtomicInteger(0)
    val workerCount = min(maxConcurrentEnrichments, enrichmentIndexes.size)

    supervisorScope {
        List(workerCount) {
            async {
                while (true) {
                    val workIndex = nextEnrichmentIndex.getAndIncrement()
                    if (workIndex >= enrichmentIndexes.size) break
                    val mediaItemIndex = enrichmentIndexes[workIndex]
                    result[mediaItemIndex] = enrich(mediaItems[mediaItemIndex])
                }
            }
        }.awaitAll()
    }
    return result
}

private fun MediaItem.requiresLocalMetadataEnrichment(): Boolean {
    val uri = localConfiguration?.uri ?: mediaId.let(android.net.Uri::parse)
    return uri.scheme == null ||
        uri.scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true) ||
        uri.scheme.equals(ContentResolver.SCHEME_FILE, ignoreCase = true)
}
