package dev.anilbeesetti.nextplayer.feature.player.cache

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class StreamCacheAnalyticsListener(
    private val streamCache: PerVideoStreamCache,
    private val scope: CoroutineScope,
) : AnalyticsListener {

    override fun onLoadStarted(eventTime: EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
        recordLoadStarted(loadEventInfo, mediaLoadData)
    }

    override fun onLoadStarted(
        eventTime: EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
        retryCount: Int,
    ) {
        if (retryCount == 0) {
            recordLoadStarted(loadEventInfo, mediaLoadData)
        }
    }

    private fun recordLoadStarted(loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
        val dataSpec = loadEventInfo.dataSpec
        val cacheKey = dataSpec.key ?: dataSpec.uri.toString()
        val format = mediaLoadData.trackFormat
        streamCache.recordKeyMetadata(
            cacheKey = cacheKey,
            trackType = mediaLoadData.trackType,
            qualityKey = qualityKey(mediaLoadData.trackType, format),
            mediaStartTimeMs = mediaLoadData.mediaStartTimeMs.takeIf { it != C.TIME_UNSET },
            mediaEndTimeMs = mediaLoadData.mediaEndTimeMs.takeIf { it != C.TIME_UNSET },
        )
    }

    override fun onLoadCompleted(eventTime: EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
    }

    override fun onDownstreamFormatChanged(eventTime: EventTime, mediaLoadData: MediaLoadData) {
        if (mediaLoadData.trackType != C.TRACK_TYPE_VIDEO) return
        val format = mediaLoadData.trackFormat ?: return
        streamCache.setCurrentVideoQualityKey(qualityKey(C.TRACK_TYPE_VIDEO, format))
        scope.launch(Dispatchers.IO) {
            streamCache.deleteOtherVideoQualities()
        }
    }

    private fun qualityKey(trackType: @C.TrackType Int, format: Format?): String? {
        if (format == null) return null
        return when (trackType) {
            C.TRACK_TYPE_VIDEO -> "v_${format.height}_${format.bitrate}"
            C.TRACK_TYPE_AUDIO -> "a_${format.language.orEmpty().lowercase(Locale.US)}_${format.bitrate}"
            else -> null
        }
    }
}
