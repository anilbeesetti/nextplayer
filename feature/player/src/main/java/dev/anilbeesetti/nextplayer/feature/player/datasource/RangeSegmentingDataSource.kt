package dev.anilbeesetti.nextplayer.feature.player.datasource

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.ContentMetadata
import java.io.IOException

@UnstableApi
internal class RangeSegmentingDataSource(
    private val cache: Cache,
    private val cacheDataSourceFactory: CacheDataSource.Factory,
    private val cacheKeyFactory: CacheKeyFactory,
    private val rangeChunkSizeBytesProvider: () -> Long,
    private val segmentConcurrentDownloadsProvider: () -> Int,
    private val segmentPrefetcher: SegmentPrefetcher,
) : DataSource {

    private val transferListeners = mutableListOf<TransferListener>()

    private var baseDataSpec: DataSpec? = null
    private var cacheKey: String? = null
    private var contentLengthBytes: Long = LENGTH_UNSET
    private var requestedEndPositionBytes: Long = LENGTH_UNSET
    private var endPositionBytes: Long = LENGTH_UNSET

    private var currentDataSource: DataSource? = null
    private var currentReadPosition: Long = 0L

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners.add(transferListener)
        currentDataSource?.addTransferListener(transferListener)
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        baseDataSpec = dataSpec
        currentReadPosition = dataSpec.position
        cacheKey = dataSpec.key ?: cacheKeyFactory.buildCacheKey(dataSpec)
        contentLengthBytes = ContentMetadata.getContentLength(cache.getContentMetadata(cacheKey!!))
        requestedEndPositionBytes = if (dataSpec.length != LENGTH_UNSET) {
            dataSpec.position + dataSpec.length
        } else {
            LENGTH_UNSET
        }
        endPositionBytes = resolveEndPosition()

        val resolvedLength = if (endPositionBytes != LENGTH_UNSET) {
            (endPositionBytes - dataSpec.position).coerceAtLeast(0L)
        } else {
            dataSpec.length
        }
        if (resolvedLength == 0L) return 0L

        openSegment(startPosition = currentReadPosition)
        return resolvedLength
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        while (true) {
            val delegate = currentDataSource ?: return C_RESULT_END_OF_INPUT
            val bytesRead = delegate.read(buffer, offset, length)
            if (bytesRead != C_RESULT_END_OF_INPUT) {
                currentReadPosition += bytesRead
                return bytesRead
            }

            // Current segment exhausted. Try opening the next one.
            delegate.close()
            currentDataSource = null

            refreshContentLengthFromCache()
            if (endPositionBytes != LENGTH_UNSET && currentReadPosition >= endPositionBytes) {
                return C_RESULT_END_OF_INPUT
            }

            if (!openNextSegment()) {
                return C_RESULT_END_OF_INPUT
            }
        }
    }

    override fun getUri(): Uri? = currentDataSource?.uri ?: baseDataSpec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = currentDataSource?.responseHeaders ?: emptyMap()

    @Throws(IOException::class)
    override fun close() {
        currentDataSource?.close()
        currentDataSource = null
        baseDataSpec = null
        cacheKey = null
        contentLengthBytes = LENGTH_UNSET
        requestedEndPositionBytes = LENGTH_UNSET
        endPositionBytes = LENGTH_UNSET
    }

    private fun refreshContentLengthFromCache() {
        val key = cacheKey ?: return
        contentLengthBytes = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        endPositionBytes = resolveEndPosition()
    }

    private fun resolveEndPosition(): Long {
        val requested = requestedEndPositionBytes
        val content = contentLengthBytes
        return when {
            requested != LENGTH_UNSET && content != LENGTH_UNSET -> minOf(requested, content)
            requested != LENGTH_UNSET -> requested
            content != LENGTH_UNSET -> content
            else -> LENGTH_UNSET
        }
    }

    private fun openNextSegment(): Boolean {
        return runCatching {
            openSegment(startPosition = currentReadPosition)
            true
        }.getOrDefault(false)
    }

    @Throws(IOException::class)
    private fun openSegment(startPosition: Long) {
        val spec = baseDataSpec ?: return
        val chunkSize = rangeChunkSizeBytesProvider().coerceAtLeast(1L)
        val segmentLength = if (endPositionBytes != LENGTH_UNSET) {
            (endPositionBytes - startPosition).coerceAtLeast(0L).coerceAtMost(chunkSize)
        } else {
            chunkSize
        }

        val segmentSpec = spec.buildUpon()
            .setPosition(startPosition)
            .setLength(segmentLength)
            .build()

        val segmentDataSource = cacheDataSourceFactory.createDataSource()
        transferListeners.forEach { segmentDataSource.addTransferListener(it) }
        currentDataSource = segmentDataSource
        currentDataSource!!.open(segmentSpec)

        refreshContentLengthFromCache()
        schedulePrefetch(startPosition = startPosition, chunkSize = chunkSize)
    }

    private fun schedulePrefetch(startPosition: Long, chunkSize: Long) {
        val concurrent = segmentConcurrentDownloadsProvider().coerceAtLeast(1)
        if (concurrent <= 1) return

        val spec = baseDataSpec ?: return
        val endPosition = endPositionBytes
        for (i in 1 until concurrent) {
            val segmentStart = startPosition + i * chunkSize
            if (endPosition != LENGTH_UNSET && segmentStart >= endPosition) break
            val length = if (endPosition != LENGTH_UNSET) {
                (endPosition - segmentStart).coerceAtLeast(0L).coerceAtMost(chunkSize)
            } else {
                chunkSize
            }
            if (length <= 0L) break

            val prefetchSpec = spec.buildUpon()
                .setPosition(segmentStart)
                .setLength(length)
                .build()
            segmentPrefetcher.prefetch(prefetchSpec)
        }
    }

    companion object {
        private const val LENGTH_UNSET: Long = -1L
        private const val C_RESULT_END_OF_INPUT = -1
    }
}
