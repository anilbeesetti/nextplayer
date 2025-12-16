package dev.anilbeesetti.nextplayer.feature.player.cache

import android.content.Context
import androidx.media3.common.C
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import timber.log.Timber

internal class PerVideoStreamCache(
    private val context: Context,
    private val cacheLimitBytesProvider: () -> Long,
) {
    private val lock = Any()

    private var activeMediaId: String? = null
    private var activeCacheDir: File? = null
    private var activeCache: SimpleCache? = null

    private val keyMetadata = ConcurrentHashMap<String, CacheKeyMetadata>()
    private var currentVideoQualityKey: String? = null

    fun getCache(): Cache? = synchronized(lock) { activeCache }

    fun setActiveMediaId(mediaId: String?) {
        val toRelease: Pair<SimpleCache?, File?> = synchronized(lock) {
            if (activeMediaId == mediaId) return
            val oldCache = activeCache
            val oldDir = activeCacheDir
            activeCache = null
            activeCacheDir = null
            activeMediaId = mediaId
            keyMetadata.clear()
            currentVideoQualityKey = null
            oldCache to oldDir
        }

        toRelease.first?.let { runCatching { it.release() } }
        toRelease.second?.let { dir -> runCatching { dir.deleteRecursively() } }

        if (mediaId.isNullOrBlank()) return

        val newDir = File(File(context.cacheDir, ROOT_DIR_NAME), stableDirNameFor(mediaId)).apply { mkdirs() }
        val cache = SimpleCache(newDir, NoOpCacheEvictor())
        synchronized(lock) {
            activeCacheDir = newDir
            activeCache = cache
        }
    }

    fun clearActiveMedia() {
        setActiveMediaId(null)
    }

    fun setCurrentVideoQualityKey(key: String?) {
        currentVideoQualityKey = key
    }

    fun recordKeyMetadata(
        cacheKey: String,
        trackType: @C.TrackType Int,
        qualityKey: String?,
        mediaStartTimeMs: Long?,
        mediaEndTimeMs: Long?,
    ) {
        keyMetadata[cacheKey] = CacheKeyMetadata(
            trackType = trackType,
            qualityKey = qualityKey,
            mediaStartTimeMs = mediaStartTimeMs,
            mediaEndTimeMs = mediaEndTimeMs,
            lastAccessTimeMs = System.currentTimeMillis(),
        )
    }

    fun touchKey(cacheKey: String) {
        keyMetadata.computeIfPresent(cacheKey) { _, old ->
            old.copy(lastAccessTimeMs = System.currentTimeMillis())
        }
    }

    fun deleteOtherVideoQualities() {
        val cache = getCache() as? SimpleCache ?: return
        val keep = currentVideoQualityKey ?: return
        val toRemove = keyMetadata.entries
            .asSequence()
            .filter { it.value.trackType == C.TRACK_TYPE_VIDEO }
            .filter { it.value.qualityKey != null && it.value.qualityKey != keep }
            .map { it.key }
            .distinct()
            .toList()

        toRemove.forEach { key ->
            runCatching { cache.removeResource(key) }
            keyMetadata.remove(key)
        }
    }

    fun enforceCacheLimit(currentPositionMs: Long, forwardWindowMs: Long) {
        val cache = getCache() as? SimpleCache ?: return
        val limitBytes = cacheLimitBytesProvider()
        if (limitBytes <= 0L) return

        deleteOtherVideoQualities()

        var total = cache.cacheSpace
        if (total <= limitBytes) return

        val windowStartMs = max(0L, currentPositionMs)
        val windowEndMs = windowStartMs + max(0L, forwardWindowMs)

        val candidates = buildList {
            cache.keys.forEach { key ->
                val meta = keyMetadata[key]
                val spans = runCatching { cache.getCachedSpans(key) }.getOrNull() ?: return@forEach
                spans.forEach { span ->
                    if (span.length <= 0L) return@forEach
                    add(EvictionCandidate(key = key, span = span, meta = meta))
                }
            }
        }

        val ordered = candidates.sortedWith(
            compareBy<EvictionCandidate> { it.priority(windowStartMs, windowEndMs) }
                .thenBy { it.orderKey(windowStartMs, windowEndMs) }
                .thenBy { it.span.lastTouchTimestamp }
                .thenBy { it.meta?.lastAccessTimeMs ?: 0L },
        )

        for (candidate in ordered) {
            if (total <= limitBytes) break
            runCatching { cache.removeSpan(candidate.span) }
                .onFailure { Timber.d(it, "Failed to evict span: ${candidate.key}") }
            total -= candidate.span.length
            runCatching { cache.getCachedSpans(candidate.key).isEmpty() }.getOrNull()?.let { empty ->
                if (empty) keyMetadata.remove(candidate.key)
            }
        }
    }

    private fun stableDirNameFor(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private data class EvictionCandidate(
        val key: String,
        val span: CacheSpan,
        val meta: CacheKeyMetadata?,
    ) {
        fun priority(windowStartMs: Long, windowEndMs: Long): Int {
            val start = meta?.mediaStartTimeMs
            val end = meta?.mediaEndTimeMs
            val type = meta?.trackType
            if (start == null || end == null || type == null) return 6

            val base = when (type) {
                C.TRACK_TYPE_VIDEO -> 0
                C.TRACK_TYPE_AUDIO -> 3
                else -> 6
            }

            val offset = when {
                end < windowStartMs -> 0
                start > windowEndMs -> 1
                else -> 2
            }
            return base + offset
        }

        fun orderKey(windowStartMs: Long, windowEndMs: Long): Long {
            val start = meta?.mediaStartTimeMs
            val end = meta?.mediaEndTimeMs
            if (start == null || end == null) return span.position
            return when {
                end < windowStartMs -> start
                start > windowEndMs -> -start
                else -> -start
            }
        }
    }

    internal data class CacheKeyMetadata(
        val trackType: @C.TrackType Int,
        val qualityKey: String?,
        val mediaStartTimeMs: Long?,
        val mediaEndTimeMs: Long?,
        val lastAccessTimeMs: Long,
    )

    companion object {
        private const val ROOT_DIR_NAME = "stream_cache"
    }
}
