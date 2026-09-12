package dev.anilbeesetti.nextplayer.feature.player.service

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.FilteringMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import dev.anilbeesetti.nextplayer.feature.player.extensions.externalAudio

@UnstableApi
internal class ExternalAudioMediaSourceFactory(
    private val delegate: MediaSource.Factory,
) : MediaSource.Factory by delegate {
    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val video = delegate.createMediaSource(mediaItem)
        val audio = mediaItem.mediaMetadata.externalAudio
        if (audio.isEmpty()) return video
        // Keep the video's duration even when a sidecar is shorter.
        return MergingMediaSource(
            true,
            false,
            video,
            *audio.map { uri ->
                FilteringMediaSource(delegate.createMediaSource(MediaItem.fromUri(uri)), C.TRACK_TYPE_AUDIO)
            }.toTypedArray(),
        )
    }
}
