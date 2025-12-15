package dev.anilbeesetti.nextplayer.feature.player.datasource

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max
import timber.log.Timber

internal class PrefetchingDiskCacheDataSourceFactory(
    context: Context,
    private val upstreamFactory: DataSource.Factory,
    cacheDirName: String = "segment_cache",
    private val maxCacheBytes: Long = 512L * 1024L * 1024L,
    private val prefetchCount: Int = 2,
    prefetchThreads: Int = 4,
) : DataSource.Factory {

    private val cacheDir = File(context.cacheDir, cacheDirName).apply { mkdirs() }
    private val executor: ExecutorService = Executors.newFixedThreadPool(max(1, prefetchThreads))
    private val inFlightPrefetches = ConcurrentHashMap<String, Any>()

    override fun createDataSource(): DataSource {
        return PrefetchingDiskCacheDataSource(
            upstreamFactory = upstreamFactory,
            cacheDir = cacheDir,
            maxCacheBytes = maxCacheBytes,
            prefetchCount = prefetchCount,
            executor = executor,
            inFlightPrefetches = inFlightPrefetches,
        )
    }

    fun shutdown() {
        executor.shutdown()
        runCatching { executor.awaitTermination(2, TimeUnit.SECONDS) }
    }
}

private class PrefetchingDiskCacheDataSource(
    private val upstreamFactory: DataSource.Factory,
    private val cacheDir: File,
    private val maxCacheBytes: Long,
    private val prefetchCount: Int,
    private val executor: ExecutorService,
    private val inFlightPrefetches: ConcurrentHashMap<String, Any>,
) : DataSource {

    private val transferListeners = mutableListOf<TransferListener>()

    private var delegate: DataSource? = null

    private var openedUri: Uri? = null
    private var responseHeaders: Map<String, List<String>> = emptyMap()

    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var cacheReadStream: FileInputStream? = null
    private var networkConnection: HttpURLConnection? = null
    private var networkReadStream: java.io.InputStream? = null

    private var cacheWriteFile: File? = null
    private var cacheWriteStream: FileOutputStream? = null
    private var cacheWriteEnabled: Boolean = false
    private var cacheWriteSucceeded: Boolean = false

    private var transferDataSpec: DataSpec? = null
    private var transferIsNetwork: Boolean = false
    private var transferStarted: Boolean = false

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners.add(transferListener)
        delegate?.addTransferListener(transferListener)
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        close()

        val uri = dataSpec.uri
        openedUri = uri

        val scheme = (uri.scheme ?: "").lowercase(Locale.US)
        if (scheme != "http" && scheme != "https") {
            delegate = upstreamFactory.createDataSource().also { ds ->
                transferListeners.forEach(ds::addTransferListener)
            }
            responseHeaders = emptyMap()
            return delegate!!.open(dataSpec).also { bytesRemaining = it }
        }

        if (dataSpec.httpMethod != DataSpec.HTTP_METHOD_GET && dataSpec.httpMethod != DataSpec.HTTP_METHOD_HEAD) {
            delegate = upstreamFactory.createDataSource().also { ds ->
                transferListeners.forEach(ds::addTransferListener)
            }
            responseHeaders = emptyMap()
            return delegate!!.open(dataSpec).also { bytesRemaining = it }
        }

        val cacheKey = cacheKeyFor(uri)
        val cacheFile = File(cacheDir, cacheKey)

        val requestedPosition = dataSpec.position
        val requestedLength = dataSpec.length

        if (cacheFile.exists() && cacheFile.isFile) {
            val fileLength = cacheFile.length()
            val canServe = if (requestedLength != C.LENGTH_UNSET.toLong()) {
                fileLength >= requestedPosition + requestedLength
            } else {
                fileLength > requestedPosition
            }
            if (canServe) {
                transferDataSpec = dataSpec
                transferIsNetwork = false
                transferListeners.forEach { it.onTransferInitializing(this, dataSpec, false) }
                cacheReadStream = FileInputStream(cacheFile).apply {
                    channel.position(requestedPosition)
                }
                transferStarted = true
                transferListeners.forEach { it.onTransferStart(this, dataSpec, false) }
                responseHeaders = emptyMap()
                bytesRemaining = if (requestedLength != C.LENGTH_UNSET.toLong()) {
                    requestedLength
                } else {
                    fileLength - requestedPosition
                }
                return bytesRemaining
            }
        }

        try {
            transferDataSpec = dataSpec
            transferIsNetwork = true
            transferListeners.forEach { it.onTransferInitializing(this, dataSpec, true) }

            val connection = (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                requestMethod = dataSpec.getHttpMethodString()
                dataSpec.httpRequestHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
                if (dataSpec.httpMethod == DataSpec.HTTP_METHOD_GET) {
                    val rangeHeader = buildRangeHeader(requestedPosition, requestedLength)
                    if (rangeHeader != null) {
                        setRequestProperty("Range", rangeHeader)
                    }
                }
            }

            networkConnection = connection
            connection.connect()
            responseHeaders = connection.headerFields.filterKeys { it != null }

            val contentLength = connection.getHeaderFieldLong("Content-Length", C.LENGTH_UNSET.toLong())
            bytesRemaining = if (requestedLength != C.LENGTH_UNSET.toLong()) {
                requestedLength
            } else {
                contentLength
            }

            networkReadStream = connection.inputStream
            transferStarted = true
            transferListeners.forEach { it.onTransferStart(this, dataSpec, true) }

            cacheWriteEnabled = dataSpec.httpMethod == DataSpec.HTTP_METHOD_GET &&
                requestedPosition == 0L &&
                requestedLength == C.LENGTH_UNSET.toLong() &&
                isCacheableUri(uri)

            if (cacheWriteEnabled) {
                val partFile = File(cacheDir, "$cacheKey.part")
                cacheWriteFile = partFile
                cacheWriteStream = FileOutputStream(partFile, false)
                cacheWriteSucceeded = false
                enqueuePrefetches(uri, dataSpec.httpRequestHeaders)
            }

            return bytesRemaining
        } catch (e: IOException) {
            close()
            throw e
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        cacheReadStream?.let { input ->
            val bytesRead = input.read(buffer, offset, length)
            if (bytesRead == -1) return C.RESULT_END_OF_INPUT
            if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                bytesRemaining -= bytesRead.toLong()
            }
            transferDataSpec?.let { spec ->
                transferListeners.forEach { it.onBytesTransferred(this, spec, false, bytesRead) }
            }
            return bytesRead
        }

        val input = networkReadStream ?: return C.RESULT_END_OF_INPUT
        val bytesRead = input.read(buffer, offset, length)
        if (bytesRead == -1) {
            if (cacheWriteEnabled) {
                cacheWriteSucceeded = true
                finalizeCacheWrite()
            }
            return C.RESULT_END_OF_INPUT
        }

        if (cacheWriteEnabled) {
            cacheWriteStream?.write(buffer, offset, bytesRead)
        }

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesRead.toLong()
        }

        transferDataSpec?.let { spec ->
            transferListeners.forEach { it.onBytesTransferred(this, spec, true, bytesRead) }
        }

        return bytesRead
    }

    override fun getUri(): Uri? = openedUri ?: delegate?.uri

    override fun getResponseHeaders(): Map<String, List<String>> {
        return if (delegate != null) delegate!!.responseHeaders else responseHeaders
    }

    @Throws(IOException::class)
    override fun close() {
        if (transferStarted) {
            transferDataSpec?.let { spec ->
                transferListeners.forEach { it.onTransferEnd(this, spec, transferIsNetwork) }
            }
        }
        transferDataSpec = null
        transferIsNetwork = false
        transferStarted = false

        runCatching { cacheReadStream?.close() }
        cacheReadStream = null

        runCatching { networkReadStream?.close() }
        networkReadStream = null

        runCatching { networkConnection?.disconnect() }
        networkConnection = null

        runCatching { cacheWriteStream?.close() }
        cacheWriteStream = null

        if (cacheWriteEnabled && !cacheWriteSucceeded) {
            cacheWriteFile?.let { runCatching { it.delete() } }
        }

        cacheWriteFile = null
        cacheWriteEnabled = false
        cacheWriteSucceeded = false

        delegate?.let { runCatching { it.close() } }
        delegate = null
    }

    private fun finalizeCacheWrite() {
        val part = cacheWriteFile ?: return
        val final = File(cacheDir, part.name.removeSuffix(".part"))
        runCatching { cacheWriteStream?.flush() }
        runCatching { cacheWriteStream?.close() }
        cacheWriteStream = null

        if (!part.exists()) return
        if (!part.renameTo(final)) {
            runCatching { part.delete() }
            return
        }

        final.setLastModified(System.currentTimeMillis())
        evictIfNeeded()
    }

    private fun enqueuePrefetches(uri: Uri, headers: Map<String, String>) {
        if (prefetchCount <= 0) return

        val nextUris = deriveNextSegmentUris(uri, prefetchCount)
        if (nextUris.isEmpty()) return

        nextUris.forEach { nextUri ->
            val key = cacheKeyFor(nextUri)
            val already = inFlightPrefetches.putIfAbsent(key, Any())
            if (already != null) return@forEach

            executor.execute {
                try {
                    prefetchToDisk(nextUri, headers)
                } catch (e: Exception) {
                    Timber.d(e, "Prefetch failed: $nextUri")
                } finally {
                    inFlightPrefetches.remove(key)
                }
            }
        }
    }

    private fun prefetchToDisk(uri: Uri, headers: Map<String, String>) {
        if (!isCacheableUri(uri)) return
        val cacheKey = cacheKeyFor(uri)
        val finalFile = File(cacheDir, cacheKey)
        if (finalFile.exists() && finalFile.isFile) return

        val partFile = File(cacheDir, "$cacheKey.part")
        if (partFile.exists()) return

        val sanitizedHeaders = headers.filterKeys { !it.equals("Range", ignoreCase = true) }
        val connection = (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            requestMethod = "GET"
            sanitizedHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        connection.connect()

        try {
            val contentLength = connection.getHeaderFieldLong("Content-Length", C.LENGTH_UNSET.toLong())
            if (contentLength != C.LENGTH_UNSET.toLong() && contentLength > MAX_PREFETCH_BYTES) {
                return
            }
            connection.inputStream.use { input ->
                FileOutputStream(partFile, false).use { output ->
                    input.copyTo(output)
                }
            }
        } finally {
            connection.disconnect()
        }

        if (!partFile.renameTo(finalFile)) {
            runCatching { partFile.delete() }
            return
        }

        finalFile.setLastModified(System.currentTimeMillis())
        evictIfNeeded()
    }

    private fun evictIfNeeded() {
        if (maxCacheBytes <= 0) return
        val files = cacheDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxCacheBytes) return

        val sorted = files.sortedBy { it.lastModified() }
        for (file in sorted) {
            if (total <= maxCacheBytes) break
            val len = file.length()
            if (file.delete()) {
                total -= len
            }
        }
    }

    private fun buildRangeHeader(position: Long, length: Long): String? {
        if (position == 0L && length == C.LENGTH_UNSET.toLong()) return null
        return if (length == C.LENGTH_UNSET.toLong()) {
            "bytes=$position-"
        } else {
            val end = position + length - 1
            "bytes=$position-$end"
        }
    }

    private fun isCacheableUri(uri: Uri): Boolean {
        val scheme = (uri.scheme ?: "").lowercase(Locale.US)
        if (scheme != "http" && scheme != "https") return false
        val last = (uri.lastPathSegment ?: "").lowercase(Locale.US)
        return last.endsWith(".m4s") ||
            last.endsWith(".m4a") ||
            last.endsWith(".webm") ||
            (last.endsWith(".mp4") && last.contains(Regex("\\d+\\.mp4$")))
    }

    private fun deriveNextSegmentUris(current: Uri, count: Int): List<Uri> {
        val s = current.toString()
        val hashIndex = s.indexOf('#').takeIf { it >= 0 } ?: s.length
        val withoutFragment = s.substring(0, hashIndex)
        val queryIndex = withoutFragment.indexOf('?').takeIf { it >= 0 } ?: withoutFragment.length
        val base = withoutFragment.substring(0, queryIndex)
        val query = if (queryIndex < withoutFragment.length) withoutFragment.substring(queryIndex) else ""

        val match = SEGMENT_NUMBER_REGEX.find(base) ?: return emptyList()
        val prefix = match.groups[1]?.value ?: return emptyList()
        val numberText = match.groups[2]?.value ?: return emptyList()
        val suffix = match.groups[3]?.value ?: return emptyList()

        val number = numberText.toLongOrNull() ?: return emptyList()
        val width = numberText.length
        return (1..count).map { delta ->
            val nextNumber = (number + delta).toString().padStart(width, '0')
            Uri.parse(prefix + nextNumber + suffix + query)
        }
    }

    private fun cacheKeyFor(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private companion object {
        private val SEGMENT_NUMBER_REGEX = Regex("^(.*?)(\\d+)(\\.[^./?]+)$")
        private const val MAX_PREFETCH_BYTES: Long = 25L * 1024L * 1024L
    }
}
