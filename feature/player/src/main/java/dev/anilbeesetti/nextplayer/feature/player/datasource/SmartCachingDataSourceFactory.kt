package dev.anilbeesetti.nextplayer.feature.player.datasource

import android.net.Uri
import androidx.media3.datasource.DataSink
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import java.io.IOException
import java.util.Locale

internal class SmartCachingDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
    private val shouldUseNoOpDataSource: (Uri) -> Boolean,
    private val noOpFactory: DataSource.Factory,
    private val cacheProvider: () -> Cache?,
    private val shouldUseRangeSegmentingDataSource: (Uri) -> Boolean = { false },
    private val rangeChunkSizeBytesProvider: () -> Long = { 1024L * 1024L },
    private val segmentConcurrentDownloadsProvider: () -> Int = { 1 },
    private val segmentPrefetcher: SegmentPrefetcher? = null,
    private val cacheKeyFactory: CacheKeyFactory = CacheKeyFactory.DEFAULT,
) : DataSource.Factory {

    override fun createDataSource(): DataSource {
        return SmartCachingDataSource(
            upstreamFactory = upstreamFactory,
            shouldUseNoOpDataSource = shouldUseNoOpDataSource,
            noOpFactory = noOpFactory,
            cacheProvider = cacheProvider,
            shouldUseRangeSegmentingDataSource = shouldUseRangeSegmentingDataSource,
            rangeChunkSizeBytesProvider = rangeChunkSizeBytesProvider,
            segmentConcurrentDownloadsProvider = segmentConcurrentDownloadsProvider,
            segmentPrefetcher = segmentPrefetcher,
            cacheKeyFactory = cacheKeyFactory,
        )
    }
}

private class SmartCachingDataSource(
    private val upstreamFactory: DataSource.Factory,
    private val shouldUseNoOpDataSource: (Uri) -> Boolean,
    private val noOpFactory: DataSource.Factory,
    private val cacheProvider: () -> Cache?,
    private val shouldUseRangeSegmentingDataSource: (Uri) -> Boolean,
    private val rangeChunkSizeBytesProvider: () -> Long,
    private val segmentConcurrentDownloadsProvider: () -> Int,
    private val segmentPrefetcher: SegmentPrefetcher?,
    private val cacheKeyFactory: CacheKeyFactory,
) : DataSource {

    private var delegate: DataSource? = null
    private val transferListeners = mutableListOf<TransferListener>()

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners.add(transferListener)
        delegate?.addTransferListener(transferListener)
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri

        val selectedFactory: DataSource.Factory = when {
            shouldUseNoOpDataSource(uri) -> noOpFactory
            isHttp(uri) -> {
                val cache = cacheProvider()
                if (cache == null) {
                    upstreamFactory
                } else if (shouldUseRangeSegmentingDataSource(uri) && segmentPrefetcher != null) {
                    rangeSegmentingDataSourceFactory(cache)
                } else {
                    cacheDataSourceFactory(cache)
                }
            }
            else -> upstreamFactory
        }

        delegate = selectedFactory.createDataSource()
        transferListeners.forEach { delegate!!.addTransferListener(it) }
        return delegate!!.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return delegate?.read(buffer, offset, length) ?: C_RESULT_END_OF_INPUT
    }

    override fun getUri(): Uri? = delegate?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders ?: emptyMap()

    @Throws(IOException::class)
    override fun close() {
        delegate?.close()
        delegate = null
    }

    private fun cacheDataSourceFactory(cache: Cache): DataSource.Factory {
        val writeSinkFactory: DataSink.Factory = CacheDataSink.Factory().setCache(cache)
        return CacheDataSource.Factory()
            .setCache(cache)
            .setCacheKeyFactory(cacheKeyFactory)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheWriteDataSinkFactory(writeSinkFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private fun rangeSegmentingDataSourceFactory(cache: Cache): DataSource.Factory {
        val baseFactory = cacheDataSourceFactory(cache) as CacheDataSource.Factory
        return DataSource.Factory {
            RangeSegmentingDataSource(
                cache = cache,
                cacheDataSourceFactory = baseFactory,
                cacheKeyFactory = cacheKeyFactory,
                rangeChunkSizeBytesProvider = rangeChunkSizeBytesProvider,
                segmentConcurrentDownloadsProvider = segmentConcurrentDownloadsProvider,
                segmentPrefetcher = requireNotNull(segmentPrefetcher),
            )
        }
    }

    private fun isHttp(uri: Uri): Boolean {
        val scheme = (uri.scheme ?: "").lowercase(Locale.US)
        return scheme == "http" || scheme == "https"
    }

    companion object {
        private const val C_RESULT_END_OF_INPUT = -1
    }
}
