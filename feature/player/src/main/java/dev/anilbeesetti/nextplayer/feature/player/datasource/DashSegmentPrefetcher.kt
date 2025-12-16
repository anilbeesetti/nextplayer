package dev.anilbeesetti.nextplayer.feature.player.datasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime
import androidx.media3.exoplayer.dash.DashSegmentIndex
import androidx.media3.exoplayer.dash.DashUtil
import androidx.media3.exoplayer.dash.DashWrappingSegmentIndex
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.dash.manifest.Representation
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@UnstableApi
internal class DashSegmentPrefetcher(
    private val dataSourceFactory: DataSource.Factory,
    private val segmentPrefetcher: SegmentPrefetcher,
    private val manifestUriProvider: () -> Uri?,
    private val isDashProvider: () -> Boolean,
    private val segmentConcurrentDownloadsProvider: () -> Int,
    private val scope: CoroutineScope,
) : AnalyticsListener {

    private val manifestLock = Mutex()
    private val manifestStateRef = AtomicReference<ManifestState?>(null)

    override fun onLoadStarted(eventTime: EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
        if (!isDashProvider()) return
        if (mediaLoadData.dataType != C.DATA_TYPE_MEDIA) return
        if (mediaLoadData.trackType != C.TRACK_TYPE_VIDEO && mediaLoadData.trackType != C.TRACK_TYPE_AUDIO) return

        val concurrent = segmentConcurrentDownloadsProvider().coerceAtLeast(1)
        if (concurrent <= 1) return

        val trackFormat = mediaLoadData.trackFormat ?: return
        val startTimeMs = mediaLoadData.mediaStartTimeMs.takeIf { it != C.TIME_UNSET } ?: return
        val manifestUri = manifestUriProvider() ?: return

        scope.launch(Dispatchers.IO) {
            val state = getOrLoadManifestState(manifestUri) ?: return@launch
            val repState = state.findRepresentation(mediaLoadData.trackType, trackFormat) ?: return@launch
            val segmentIndex = repState.getOrLoadSegmentIndex(trackType = mediaLoadData.trackType) ?: return@launch

            val periodDurationUs = state.manifest.getPeriodDurationUs(repState.periodIndex)
            val currentTimeUs = C.msToUs(startTimeMs)
            val currentSegmentNum = segmentIndex.getSegmentNum(currentTimeUs, periodDurationUs)

            val maxSegmentNum = maxAvailableSegmentNum(segmentIndex, periodDurationUs) ?: return@launch
            for (i in 1 until concurrent) {
                val nextNum = currentSegmentNum + i
                if (nextNum > maxSegmentNum) break
                val rangedUri = segmentIndex.getSegmentUrl(nextNum)
                val dataSpec = DashUtil.buildDataSpec(
                    repState.representation,
                    repState.baseUrl,
                    rangedUri,
                    0,
                    emptyMap(),
                )
                segmentPrefetcher.prefetch(dataSpec)
            }
        }
    }

    private suspend fun getOrLoadManifestState(manifestUri: Uri): ManifestState? {
        val cached = manifestStateRef.get()
        if (cached != null && cached.manifestUri == manifestUri) return cached

        return manifestLock.withLock {
            val current = manifestStateRef.get()
            if (current != null && current.manifestUri == manifestUri) return@withLock current

            val manifest = loadManifest(manifestUri) ?: return@withLock null
            val state = ManifestState(
                manifestUri = manifestUri,
                manifest = manifest,
                representations = buildRepresentations(manifest),
            )
            manifestStateRef.set(state)
            state
        }
    }

    private suspend fun loadManifest(uri: Uri): DashManifest? = withContext(Dispatchers.IO) {
        runCatching {
            val dataSource = dataSourceFactory.createDataSource()
            try {
                DashUtil.loadManifest(dataSource, uri)
            } finally {
                runCatching { dataSource.close() }
            }
        }.getOrNull()
    }

    private fun buildRepresentations(manifest: DashManifest): List<RepresentationState> {
        val states = mutableListOf<RepresentationState>()
        for (periodIndex in 0 until manifest.getPeriodCount()) {
            val period = manifest.getPeriod(periodIndex)
            period.adaptationSets.forEach { set ->
                if (set.type != C.TRACK_TYPE_VIDEO && set.type != C.TRACK_TYPE_AUDIO) return@forEach
                set.representations.forEach { representation ->
                    val baseUrl = representation.baseUrls.firstOrNull()?.url ?: return@forEach
                    states += RepresentationState(
                        periodIndex = periodIndex,
                        trackType = set.type,
                        representation = representation,
                        baseUrl = baseUrl,
                    )
                }
            }
        }
        return states
    }

    private fun maxAvailableSegmentNum(index: DashSegmentIndex, periodDurationUs: Long): Long? {
        val segmentCount = index.getSegmentCount(periodDurationUs)
        if (segmentCount != DashSegmentIndex.INDEX_UNBOUNDED.toLong()) {
            if (segmentCount <= 0) return null
            return index.getFirstSegmentNum() + segmentCount - 1
        }

        val nowUs = Util.msToUs(System.currentTimeMillis())
        val firstAvailable = index.getFirstAvailableSegmentNum(periodDurationUs, nowUs)
        val availableCount = index.getAvailableSegmentCount(periodDurationUs, nowUs)
        if (availableCount <= 0) return null
        return firstAvailable + availableCount - 1
    }

    private data class ManifestState(
        val manifestUri: Uri,
        val manifest: DashManifest,
        val representations: List<RepresentationState>,
    ) {
        fun findRepresentation(trackType: @C.TrackType Int, format: Format): RepresentationState? {
            val candidates = representations.filter { it.trackType == trackType }
            val id = format.id
            if (!id.isNullOrBlank()) {
                candidates.firstOrNull { it.representation.format.id == id }?.let { return it }
            }

            return candidates.minByOrNull { it.matchScore(format) }
                ?.takeIf { it.matchScore(format) < Int.MAX_VALUE }
        }
    }

    private inner class RepresentationState(
        val periodIndex: Int,
        val trackType: @C.TrackType Int,
        val representation: Representation,
        val baseUrl: String,
    ) {
        @Volatile
        private var cachedIndex: DashSegmentIndex? = representation.getIndex() ?: (representation as? DashSegmentIndex)

        suspend fun getOrLoadSegmentIndex(trackType: @C.TrackType Int): DashSegmentIndex? {
            cachedIndex?.let { return it }
            return withContext(Dispatchers.IO) {
                runCatching {
                    val dataSource = dataSourceFactory.createDataSource()
                    try {
                        val chunkIndex = DashUtil.loadChunkIndex(dataSource, trackType, representation)
                            ?: return@runCatching null
                        DashWrappingSegmentIndex(chunkIndex, representation.presentationTimeOffsetUs)
                    } finally {
                        runCatching { dataSource.close() }
                    }
                }.getOrNull()
            }?.also { cachedIndex = it }
        }

        fun matchScore(target: Format): Int {
            val candidate = representation.format
            return when (trackType) {
                C.TRACK_TYPE_VIDEO -> {
                    if (target.height > 0 && candidate.height > 0 && target.height != candidate.height) return Int.MAX_VALUE
                    if (target.bitrate > 0 && candidate.bitrate > 0) {
                        kotlin.math.abs(target.bitrate - candidate.bitrate)
                    } else {
                        Int.MAX_VALUE / 2
                    }
                }
                C.TRACK_TYPE_AUDIO -> {
                    val langScore = if (!target.language.isNullOrBlank() && target.language == candidate.language) 0 else 10_000
                    val bitrateScore = if (target.bitrate > 0 && candidate.bitrate > 0) kotlin.math.abs(target.bitrate - candidate.bitrate) else 50_000
                    langScore + bitrateScore
                }
                else -> Int.MAX_VALUE
            }
        }
    }
}
