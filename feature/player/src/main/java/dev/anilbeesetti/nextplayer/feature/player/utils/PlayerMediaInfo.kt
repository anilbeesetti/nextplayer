package dev.anilbeesetti.nextplayer.feature.player.utils

import android.content.ContentResolver
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.core.common.Utils
import dev.anilbeesetti.nextplayer.core.model.AudioStreamInfo
import dev.anilbeesetti.nextplayer.core.model.MediaInfo
import dev.anilbeesetti.nextplayer.core.model.SubtitleStreamInfo
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.model.VideoStreamInfo

@OptIn(UnstableApi::class)
internal fun Player.mediaInfoSnapshot(fallbackName: String, renamedName: String?): MediaInfo? {
    val mediaItem = currentMediaItem ?: return null
    val uri = mediaItem.localConfiguration?.uri
    val path = uri?.takeIf { it.scheme == ContentResolver.SCHEME_FILE }?.path.orEmpty()
    val groups = currentTracks.groups
    val videoGroups = groups.filter { it.type == C.TRACK_TYPE_VIDEO }
    val audioGroups = groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val subtitleGroups = groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val videoFormat = (videoGroups.firstOrNull { it.isSelected } ?: videoGroups.firstOrNull())
        ?.representativeFormat()
    val size = videoSize
    val width = size.width.takeIf { it > 0 } ?: videoFormat?.width ?: 0
    val height = size.height.takeIf { it > 0 } ?: videoFormat?.height ?: 0
    val durationMs = duration.takeIf { it > 0 } ?: 0L
    val name = renamedName ?: mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
        ?: path.substringAfterLast('/').ifBlank { fallbackName }
    return MediaInfo(
        video = Video(
            id = 0,
            path = path,
            parentPath = path.substringBeforeLast(
                '/',
                missingDelimiterValue = "",
            ),
            nameWithExtension = name,
            uriString = uri?.toString().orEmpty(),
            duration = durationMs,
            width = width,
            height = height,
            size = 0,
            format = videoFormat?.containerMimeType,
            formattedDuration = durationMs.takeIf { it > 0 }?.let(Utils::formatDurationMillis)
                .orEmpty(),
        ),
        videoStream = if (videoFormat != null || (width > 0 && height > 0)) {
            VideoStreamInfo(
                index = 0,
                title = videoFormat?.label,
                codecName = videoFormat?.codecName().orEmpty(),
                language = videoFormat?.language,
                disposition = 0,
                bitRate = videoFormat?.bitrate?.toLong() ?: 0,
                frameRate = videoFormat?.frameRate?.toDouble() ?: 0.0,
                frameWidth = width,
                frameHeight = height,
            )
        } else {
            null
        },
        audioStreams = audioGroups.mapIndexed { index, group ->
            val format = group.representativeFormat()
            AudioStreamInfo(
                index = index,
                title = format.label,
                codecName = format.codecName(),
                language = format.language,
                disposition = 0,
                bitRate = format.bitrate.toLong(),
                sampleFormat = null,
                sampleRate = format.sampleRate,
                channels = format.channelCount,
                channelLayout = null,
            )
        },
        subtitleStreams = subtitleGroups.mapIndexed { index, group ->
            val format = group.representativeFormat()
            SubtitleStreamInfo(
                index = index,
                title = format.label,
                codecName = format.codecName(),
                language = format.language,
                disposition = 0,
            )
        },
    )
}

private fun Tracks.Group.representativeFormat(): Format =
    getTrackFormat((0 until length).firstOrNull(::isTrackSelected) ?: 0)

private fun Format.codecName(): String = codecs ?: sampleMimeType.orEmpty()
