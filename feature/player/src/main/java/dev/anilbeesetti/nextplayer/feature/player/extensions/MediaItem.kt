package dev.anilbeesetti.nextplayer.feature.player.extensions

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

private const val MEDIA_METADATA_POSITION_KEY = "media_metadata_position"
private const val MEDIA_METADATA_PLAYBACK_SPEED_KEY = "media_metadata_playback_speed"
private const val MEDIA_METADATA_AUDIO_TRACK_INDEX_KEY = "audio_track_index"
private const val MEDIA_METADATA_SUBTITLE_TRACK_INDEX_KEY = "subtitle_track_index"
private const val MEDIA_METADATA_VIDEO_ZOOM_KEY = "media_metadata_video_zoom"

private fun Bundle.setExtras(
    positionMs: Long?,
    videoScale: Float?,
    playbackSpeed: Float?,
    audioTrackIndex: Int?,
    subtitleTrackIndex: Int?,
) = apply {
    positionMs?.let { putLong(MEDIA_METADATA_POSITION_KEY, it) }
    videoScale?.let { putFloat(MEDIA_METADATA_VIDEO_ZOOM_KEY, it) }
    playbackSpeed?.let { putFloat(MEDIA_METADATA_PLAYBACK_SPEED_KEY, it) }
    audioTrackIndex?.let { putInt(MEDIA_METADATA_AUDIO_TRACK_INDEX_KEY, it) }
    subtitleTrackIndex?.let { putInt(MEDIA_METADATA_SUBTITLE_TRACK_INDEX_KEY, it) }
}

fun MediaMetadata.Builder.setExtras(
    positionMs: Long? = null,
    videoScale: Float? = null,
    playbackSpeed: Float? = null,
    audioTrackIndex: Int? = null,
    subtitleTrackIndex: Int? = null,
) = setExtras(
    Bundle().setExtras(
        positionMs = positionMs,
        videoScale = videoScale,
        playbackSpeed = playbackSpeed,
        audioTrackIndex = audioTrackIndex,
        subtitleTrackIndex = subtitleTrackIndex,
    ),
)

val MediaMetadata.positionMs: Long?
    get() = extras?.run {
        getLong(MEDIA_METADATA_POSITION_KEY)
            .takeIf { containsKey(MEDIA_METADATA_POSITION_KEY) }
    }

val MediaMetadata.playbackSpeed: Float?
    get() = extras?.run {
        getFloat(MEDIA_METADATA_PLAYBACK_SPEED_KEY)
            .takeIf { containsKey(MEDIA_METADATA_PLAYBACK_SPEED_KEY) }
    }

val MediaMetadata.audioTrackIndex: Int?
    get() = extras?.run {
        getInt(MEDIA_METADATA_AUDIO_TRACK_INDEX_KEY)
            .takeIf { containsKey(MEDIA_METADATA_AUDIO_TRACK_INDEX_KEY) }
    }

val MediaMetadata.subtitleTrackIndex: Int?
    get() = extras?.run {
        getInt(MEDIA_METADATA_SUBTITLE_TRACK_INDEX_KEY)
            .takeIf { containsKey(MEDIA_METADATA_SUBTITLE_TRACK_INDEX_KEY) }
    }

val MediaMetadata.videoZoom: Float?
    get() = extras?.run {
        getFloat(MEDIA_METADATA_VIDEO_ZOOM_KEY)
            .takeIf { containsKey(MEDIA_METADATA_VIDEO_ZOOM_KEY) }
    }

fun MediaItem.copy(
    positionMs: Long? = this.mediaMetadata.positionMs,
    videoZoom: Float? = this.mediaMetadata.videoZoom,
    playbackSpeed: Float? = this.mediaMetadata.playbackSpeed,
    audioTrackIndex: Int? = this.mediaMetadata.audioTrackIndex,
    subtitleTrackIndex: Int? = this.mediaMetadata.subtitleTrackIndex,
) = buildUpon().setMediaMetadata(
    mediaMetadata.buildUpon().setExtras(
        Bundle(mediaMetadata.extras).setExtras(
            positionMs = positionMs,
            videoScale = videoZoom,
            playbackSpeed = playbackSpeed,
            audioTrackIndex = audioTrackIndex,
            subtitleTrackIndex = subtitleTrackIndex,
        ),
    ).build(),
).build()
