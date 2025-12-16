package dev.anilbeesetti.nextplayer.feature.player.cache

import android.content.Context
import androidx.media3.common.C
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dev.anilbeesetti.nextplayer.core.common.cache.StreamCacheStorage
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

internal class PerVideoStreamCache(
    private val context: Context,
) {
    private val lock = Any()

    private var activeMediaId: String? = null
    private var activeCacheDir: File? = null
    private var activeCache: SimpleCache? = null

    private val keyMetadata = ConcurrentHashMap<String, CacheKeyMetadata>()
    private var currentVideoQualityKey: String? = null

    fun getCache(): Cache? = synchronized(lock) { activeCache }

    fun close(deleteFiles: Boolean) {
        val toRelease: Pair<SimpleCache?, File?> = synchronized(lock) {
            val oldCache = activeCache
            val oldDir = activeCacheDir
            activeCache = null
            activeCacheDir = null
            activeMediaId = null
            keyMetadata.clear()
            currentVideoQualityKey = null
            oldCache to oldDir
        }

        toRelease.first?.let { runCatching { it.release() } }
        if (deleteFiles) {
            toRelease.second?.let { dir -> runCatching { dir.deleteRecursively() } }
        }
    }

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

        val rootDir = StreamCacheStorage.rootDir(context).apply { mkdirs() }
        val newDirName = stableDirNameFor(mediaId)
        val newDir = File(rootDir, newDirName).apply { mkdirs() }
        rootDir.listFiles()?.forEach { child ->
            if (child.absolutePath != newDir.absolutePath) {
                runCatching { child.deleteRecursively() }
            }
        }
        val cache = SimpleCache(newDir, NoOpCacheEvictor())
        synchronized(lock) {
            activeCacheDir = newDir
            activeCache = cache
        }
    }

    fun clearActiveMedia() {
        close(deleteFiles = true)
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
        )
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

    private fun stableDirNameFor(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    internal data class CacheKeyMetadata(
        val trackType: @C.TrackType Int,
        val qualityKey: String?,
        val mediaStartTimeMs: Long?,
        val mediaEndTimeMs: Long?,
    )
}
