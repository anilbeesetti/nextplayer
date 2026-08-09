package dev.anilbeesetti.nextplayer.feature.player.service

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.ClippingMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import dev.anilbeesetti.nextplayer.feature.player.extensions.externalAudioTrackUris

@UnstableApi
class ExternalAudioMediaSourceFactory(
    private val delegate: MediaSource.Factory,
) : MediaSource.Factory {

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val primary = delegate.createMediaSource(mediaItem)
        val audioSources = mediaItem.mediaMetadata.externalAudioTrackUris.map { uri ->
            delegate.createMediaSource(MediaItem.fromUri(uri))
        }
        return if (audioSources.isEmpty()) {
            primary
        } else {
            val mergedSource = MergingMediaSource(primary, *audioSources.toTypedArray())
            mediaItem.mediaMetadata.durationMs
                ?.takeIf { it > 0 }
                ?.let { primaryDurationMs ->
                    ClippingMediaSource.Builder(mergedSource)
                        .setEndPositionMs(primaryDurationMs)
                        .build()
                } ?: mergedSource
        }
    }

    override fun getSupportedTypes(): IntArray = delegate.supportedTypes

    override fun setDrmSessionManagerProvider(
        drmSessionManagerProvider: DrmSessionManagerProvider,
    ): MediaSource.Factory {
        delegate.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
    ): MediaSource.Factory {
        delegate.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }
}
