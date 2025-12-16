package dev.anilbeesetti.nextplayer.feature.player.cache

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.StreamKey
import androidx.media3.common.Tracks
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.dash.DashSegmentIndex
import androidx.media3.exoplayer.dash.DashUtil
import androidx.media3.exoplayer.dash.manifest.AdaptationSet
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.dash.manifest.Period
import androidx.media3.exoplayer.dash.manifest.RangedUri
import androidx.media3.exoplayer.dash.manifest.Representation
import androidx.media3.exoplayer.dash.offline.DashDownloader
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

internal class DashPrefetcher(
    private val scope: CoroutineScope,
    private val streamCache: PerVideoStreamCache,
    private val cacheDataSourceFactoryProvider: () -> CacheDataSource.Factory?,
    private val maxThreadsProvider: () -> Int,
    private val maxBufferMsProvider: () -> Int,
) {
    private var periodicJob: Job? = null
    private var activeJob: Job? = null
    private var activeExecutor: ExecutorService? = null

    @Volatile
    private var activeDownloader: DashDownloader? = null

    @Volatile
    private var lastRequestedSpec: PrefetchSpec? = null

    fun onMediaChanged(player: Player) {
        cancelActive()
        lastRequestedSpec = null
    }

    fun setPlaying(player: Player, isPlaying: Boolean) {
        if (!isPlaying) {
            stopPeriodic()
            return
        }
        val currentMediaItem = player.currentMediaItem
        val mediaId = currentMediaItem?.mediaId
        if (mediaId.isNullOrBlank()) return
        if (!isDashUri(Uri.parse(mediaId))) return

        if (periodicJob?.isActive == true) return
        periodicJob = scope.launch {
            while (isActive) {
                runCatching {
                    maybePrefetch(player)
                }
                delay(1_250)
            }
        }
    }

    fun onSeek(player: Player) {
        stopPeriodic()
        cancelActive()
        val currentMediaItem = player.currentMediaItem ?: return
        if (!isDashUri(Uri.parse(currentMediaItem.mediaId))) return
        scope.launch {
            delay(300)
            maybePrefetch(player)
        }
    }

    fun cancelActive() {
        activeJob?.cancel()
        activeJob = null
        runCatching { activeDownloader?.cancel() }
        activeDownloader = null
        activeExecutor?.shutdownNow()
        activeExecutor = null
    }

    fun shutdown() {
        stopPeriodic()
        cancelActive()
    }

    private fun stopPeriodic() {
        periodicJob?.cancel()
        periodicJob = null
    }

    private suspend fun maybePrefetch(player: Player) {
        val mediaItem = player.currentMediaItem ?: return
        val tracks = player.currentTracks
        val mediaUri = runCatching { Uri.parse(mediaItem.mediaId) }.getOrNull() ?: return
        if (!isDashUri(mediaUri)) return

        val cacheFactory = cacheDataSourceFactoryProvider() ?: return

        val maxBufferMs = max(0, maxBufferMsProvider())
        val windowUs = maxBufferMs.toLong() * 1000L
        if (windowUs <= 0L) return

        val selected = SelectedFormats.fromTracks(tracks) ?: return

        val currentPositionMs = player.currentPosition
        val currentPositionUs = currentPositionMs * 1000L
        val bufferedPositionUs = player.bufferedPosition * 1000L
        val prefetchStartUs = max(currentPositionUs, bufferedPositionUs)
        val prefetchEndUs = currentPositionUs + windowUs
        if (prefetchEndUs - prefetchStartUs < 1_000_000L) return
        val periodIndexSnapshot = player.currentPeriodIndex

        val desiredSpec = PrefetchSpec(
            mediaId = mediaItem.mediaId,
            videoKey = selected.video?.let { qualityKey(C.TRACK_TYPE_VIDEO, it) },
            audioKey = selected.audio?.let { qualityKey(C.TRACK_TYPE_AUDIO, it) },
            periodIndex = periodIndexSnapshot,
            startUs = prefetchStartUs,
            endUs = prefetchEndUs,
        )

        if (activeJob?.isActive == true) return
        lastRequestedSpec?.let { previous ->
            if (previous.covers(desiredSpec)) return
        }

        activeJob?.cancel()
        activeExecutor?.shutdownNow()

        activeJob = scope.launch(Dispatchers.IO) {
            try {
                val manifest = loadDashManifest(cacheFactory, mediaUri)
                val periodIndex = desiredSpec.periodIndex.coerceIn(0, manifest.periodCount - 1)
                val period = manifest.getPeriod(periodIndex)

                val videoKey = findStreamKey(periodIndex, period, C.TRACK_TYPE_VIDEO, selected.video)
                val audioKey = findStreamKey(periodIndex, period, C.TRACK_TYPE_AUDIO, selected.audio)
                if (videoKey == null && audioKey == null) return@launch

                val streamKeys = buildList {
                    videoKey?.let(::add)
                    audioKey?.let(::add)
                }

                val (_, videoIndex) = videoKey?.let { repFor(period, it, C.TRACK_TYPE_VIDEO) } ?: (null to null)
                val segmentDurUs = videoIndex?.let { estimateSegmentDurationUs(it, periodIndex, manifest) } ?: 10_000_000L
                val threads = computeThreads(windowUs, segmentDurUs)

                val executor = Executors.newFixedThreadPool(threads)
                activeExecutor = executor
                lastRequestedSpec = desiredSpec

                registerWindowMetadata(
                    manifest = manifest,
                    period = period,
                    videoKey = videoKey,
                    audioKey = audioKey,
                    periodIndex = periodIndex,
                    startUs = prefetchStartUs,
                    endUs = prefetchEndUs,
                )

                val downloadItem = MediaItem.Builder()
                    .setUri(mediaUri)
                    .setStreamKeys(streamKeys)
                    .build()

                val downloader = DashDownloader.Factory(cacheFactory)
                    .setExecutor(executor)
                    .setStartPositionUs(prefetchStartUs)
                    .setDurationUs(prefetchEndUs - prefetchStartUs)
                    .create(downloadItem)

                try {
                    activeDownloader = downloader
                    downloader.download(null)
                } finally {
                    activeDownloader = null
                    executor.shutdownNow()
                    streamCache.enforceCacheLimit(
                        currentPositionMs = currentPositionMs,
                        forwardWindowMs = maxBufferMs.toLong(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.d(e, "DASH prefetch failed")
            }
        }
    }

    private fun computeThreads(windowUs: Long, segmentDurUs: Long): Int {
        val maxThreads = max(1, maxThreadsProvider())
        val raw = if (segmentDurUs <= 0L) 1.0 else windowUs.toDouble() / segmentDurUs.toDouble()
        return min(maxThreads, max(1, ceil(raw).toInt()))
    }

    private suspend fun loadDashManifest(cacheFactory: CacheDataSource.Factory, uri: Uri): DashManifest {
        return withContext(Dispatchers.IO) {
            val dataSource = cacheFactory.createDataSourceForDownloading()
            DashUtil.loadManifest(dataSource, uri)
        }
    }

    private fun findStreamKey(
        periodIndex: Int,
        period: Period,
        trackType: @C.TrackType Int,
        selectedFormat: Format?,
    ): StreamKey? {
        if (selectedFormat == null) return null
        val adaptationSets = period.adaptationSets
        for (adaptationSetIndex in adaptationSets.indices) {
            val adaptationSet = adaptationSets[adaptationSetIndex]
            if (adaptationSet.type != trackType) continue
            val representationIndex = matchRepresentationIndex(adaptationSet, selectedFormat) ?: continue
            return StreamKey(periodIndex, adaptationSetIndex, representationIndex)
        }
        return null
    }

    private fun repFor(period: Period, key: StreamKey, trackType: @C.TrackType Int): Pair<Representation?, DashSegmentIndex?> {
        val adaptationSet = period.adaptationSets.getOrNull(key.groupIndex) ?: return null to null
        if (adaptationSet.type != trackType) return null to null
        val rep = adaptationSet.representations.getOrNull(key.streamIndex) ?: return null to null
        return rep to rep.getIndex()
    }

    private fun estimateSegmentDurationUs(index: DashSegmentIndex, periodIndex: Int, manifest: DashManifest): Long {
        val periodDurationUs = manifest.getPeriodDurationUs(periodIndex)
        val segNum = index.getSegmentNum(0L, periodDurationUs)
        return max(1L, index.getDurationUs(segNum, periodDurationUs))
    }

    private fun matchRepresentationIndex(adaptationSet: AdaptationSet, selected: Format): Int? {
        val reps = adaptationSet.representations
        val exact = reps.indexOfFirst { rep ->
            val f = rep.format
            selected.id != null && selected.id == f.id
        }
        if (exact >= 0) return exact

        val byMetrics = reps.indexOfFirst { rep ->
            val f = rep.format
            selected.bitrate == f.bitrate &&
                selected.height == f.height &&
                selected.width == f.width &&
                selected.sampleMimeType == f.sampleMimeType
        }
        if (byMetrics >= 0) return byMetrics

        val byBitrate = reps.indexOfFirst { rep -> rep.format.bitrate == selected.bitrate }
        return byBitrate.takeIf { it >= 0 }
    }

    private fun registerWindowMetadata(
        manifest: DashManifest,
        period: Period,
        videoKey: StreamKey?,
        audioKey: StreamKey?,
        periodIndex: Int,
        startUs: Long,
        endUs: Long,
    ) {
        val periodDurationUs = manifest.getPeriodDurationUs(periodIndex)
        listOfNotNull(
            videoKey?.let { it to C.TRACK_TYPE_VIDEO },
            audioKey?.let { it to C.TRACK_TYPE_AUDIO },
        ).forEach { (key, trackType) ->
            val adaptationSet = period.adaptationSets.getOrNull(key.groupIndex) ?: return@forEach
            val rep = adaptationSet.representations.getOrNull(key.streamIndex) ?: return@forEach
            val index = rep.getIndex() ?: return@forEach

            rep.getInitializationUri()?.let { initUri ->
                val initKey = DashUtil.resolveCacheKey(rep, initUri)
                streamCache.recordKeyMetadata(
                    cacheKey = initKey,
                    trackType = trackType,
                    qualityKey = qualityKey(trackType, rep.format),
                    mediaStartTimeMs = 0L,
                    mediaEndTimeMs = 0L,
                )
            }

            val first = index.getSegmentNum(startUs, periodDurationUs)
            val last = index.getSegmentNum(endUs, periodDurationUs)
            for (segNum in first..last) {
                val rangedUri: RangedUri = index.getSegmentUrl(segNum)
                val cacheKey = DashUtil.resolveCacheKey(rep, rangedUri)
                val segStartUs = index.getTimeUs(segNum)
                val segDurUs = index.getDurationUs(segNum, periodDurationUs)
                streamCache.recordKeyMetadata(
                    cacheKey = cacheKey,
                    trackType = trackType,
                    qualityKey = qualityKey(trackType, rep.format),
                    mediaStartTimeMs = segStartUs / 1000L,
                    mediaEndTimeMs = (segStartUs + segDurUs) / 1000L,
                )
            }
        }
    }

    private fun qualityKey(trackType: @C.TrackType Int, format: Format): String? {
        return when (trackType) {
            C.TRACK_TYPE_VIDEO -> "v_${format.height}_${format.bitrate}"
            C.TRACK_TYPE_AUDIO -> "a_${format.language ?: ""}_${format.bitrate}"
            else -> null
        }
    }

    private fun isDashUri(uri: Uri): Boolean {
        val last = (uri.lastPathSegment ?: "").lowercase(Locale.US)
        return last.endsWith(".mpd")
    }

    private data class SelectedFormats(
        val video: Format?,
        val audio: Format?,
    ) {
        companion object {
            fun fromTracks(tracks: Tracks): SelectedFormats? {
                var video: Format? = null
                var audio: Format? = null
                tracks.groups.forEach { group ->
                    for (i in 0 until group.length) {
                        if (!group.isTrackSelected(i)) continue
                        val format = group.getTrackFormat(i)
                        when (group.type) {
                            C.TRACK_TYPE_VIDEO -> video = format
                            C.TRACK_TYPE_AUDIO -> audio = format
                        }
                    }
                }
                return SelectedFormats(video = video, audio = audio)
            }
        }
    }

    private data class PrefetchSpec(
        val mediaId: String,
        val videoKey: String?,
        val audioKey: String?,
        val periodIndex: Int,
        val startUs: Long,
        val endUs: Long,
    ) {
        fun covers(other: PrefetchSpec): Boolean {
            if (mediaId != other.mediaId) return false
            if (videoKey != other.videoKey) return false
            if (audioKey != other.audioKey) return false
            if (periodIndex != other.periodIndex) return false
            return other.startUs >= startUs && other.endUs <= endUs
        }
    }
}
