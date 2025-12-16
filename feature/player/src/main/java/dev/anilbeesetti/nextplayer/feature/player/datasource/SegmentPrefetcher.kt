package dev.anilbeesetti.nextplayer.feature.player.datasource

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@UnstableApi
internal class SegmentPrefetcher(
    private val upstreamFactory: DataSource.Factory,
    private val cacheProvider: () -> Cache?,
    private val cacheKeyFactory: CacheKeyFactory = CacheKeyFactory.DEFAULT,
    private val scope: CoroutineScope,
) {
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    fun prefetch(dataSpec: DataSpec) {
        val cache = cacheProvider() ?: return
        val key = dataSpec.key ?: cacheKeyFactory.buildCacheKey(dataSpec)
        val lengthToCheck = resolveLengthToCheck(cache, key, dataSpec)
        if (lengthToCheck != LENGTH_UNSET && cache.isCached(key, dataSpec.position, lengthToCheck)) return

        val jobKey = buildString {
            append(key)
            append(':')
            append(dataSpec.position)
            append(':')
            append(dataSpec.length)
        }
        if (!inFlight.add(jobKey)) return

        scope.launch(Dispatchers.IO) {
            try {
                val cacheDataSource = createCacheDataSource(cache)
                CacheWriter(cacheDataSource, dataSpec, null, null).cache()
            } catch (_: Exception) {
                // Best-effort prefetch; ignore failures (network, cancellations, cache contention).
            } finally {
                inFlight.remove(jobKey)
            }
        }
    }

    private fun resolveLengthToCheck(cache: Cache, key: String, dataSpec: DataSpec): Long {
        if (dataSpec.length != LENGTH_UNSET) return dataSpec.length
        val totalLength = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        if (totalLength == LENGTH_UNSET) return LENGTH_UNSET
        return (totalLength - dataSpec.position).coerceAtLeast(0L)
    }

    private fun createCacheDataSource(cache: Cache): CacheDataSource {
        val writeSinkFactory = CacheDataSink.Factory().setCache(cache)
        return CacheDataSource.Factory()
            .setCache(cache)
            .setCacheKeyFactory(cacheKeyFactory)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheWriteDataSinkFactory(writeSinkFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .createDataSource() as CacheDataSource
    }

    private companion object {
        private const val LENGTH_UNSET: Long = -1L
    }
}
