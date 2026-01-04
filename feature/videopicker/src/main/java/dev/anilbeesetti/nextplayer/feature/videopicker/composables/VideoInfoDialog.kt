package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.common.Utils
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog

@Composable
fun VideoInfoDialog(
    video: Video,
    onDismiss: () -> Unit,
) {
    NextDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = video.displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        content = {
            HorizontalDivider()
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                MediaInfoTitle(text = stringResource(R.string.file))
                MediaInfoText(
                    title = stringResource(id = R.string.file),
                    subText = video.nameWithExtension,
                )
                MediaInfoText(
                    title = stringResource(id = R.string.location),
                    subText = video.parentPath,
                )
                MediaInfoText(
                    title = stringResource(id = R.string.size),
                    subText = video.formattedFileSize,
                )
                MediaInfoText(
                    title = stringResource(id = R.string.duration),
                    subText = video.formattedDuration,
                )
                video.format?.let {
                    MediaInfoText(
                        title = stringResource(id = R.string.format),
                        subText = it,
                    )
                }
                video.videoStream?.let { videoStream ->
                    MediaInfoTitle(text = stringResource(id = R.string.video_track))
                    videoStream.title?.let {
                        MediaInfoText(
                            title = stringResource(id = R.string.title),
                            subText = it,
                        )
                    }
                    MediaInfoText(
                        title = stringResource(id = R.string.codec),
                        subText = videoStream.codecName,
                    )
                    MediaInfoText(
                        title = stringResource(id = R.string.resolution),
                        subText = "${videoStream.frameWidth} x ${videoStream.frameHeight}",
                    )
                    MediaInfoText(
                        title = stringResource(id = R.string.frame_rate),
                        subText = videoStream.frameRate.toInt().toString(),
                    )
                    Utils.formatBitrate(videoStream.bitRate)?.let {
                        MediaInfoText(
                            title = stringResource(id = R.string.bitrate),
                            subText = it,
                        )
                    }
                }
                video.audioStreams.forEachIndexed { index, audioStream ->
                    MediaInfoTitle(text = "${stringResource(id = R.string.audio_track)} #${index + 1}")
                    audioStream.title?.let {
                        MediaInfoText(
                            title = stringResource(id = R.string.title),
                            subText = it,
                        )
                    }
                    MediaInfoText(
                        title = stringResource(id = R.string.codec),
                        subText = audioStream.codecName,
                    )
                    MediaInfoText(
                        title = stringResource(id = R.string.sample_rate),
                        subText = "${audioStream.sampleRate} Hz",
                    )
                    MediaInfoText(
                        title = stringResource(id = R.string.sample_format),
                        subText = audioStream.sampleFormat.toString(),
                    )
                    Utils.formatBitrate(audioStream.bitRate)?.let {
                        MediaInfoText(
                            title = stringResource(id = R.string.bitrate),
                            subText = it,
                        )
                    }
                    MediaInfoText(
                        title = stringResource(id = R.string.channels),
                        subText = audioStream.channelLayout ?: audioStream.channels.toString(),
                    )
                    Utils.formatLanguage(audioStream.language)?.let {
                        MediaInfoText(
                            title = stringResource(id = R.string.language),
                            subText = it,
                        )
                    }
                }
                video.subtitleStreams.forEachIndexed { index, subtitleStream ->
                    MediaInfoTitle(text = "${stringResource(id = R.string.subtitle_track)} #${index + 1}")
                    subtitleStream.title?.let {
                        MediaInfoText(
                            title = stringResource(id = R.string.title),
                            subText = it,
                        )
                    }
                    MediaInfoText(
                        title = stringResource(id = R.string.codec),
                        subText = subtitleStream.codecName,
                    )
                    Utils.formatLanguage(subtitleStream.language)?.let {
                        MediaInfoText(
                            title = stringResource(id = R.string.language),
                            subText = it,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.okay))
            }
        },
    )
}

@Composable
private fun MediaInfoTitle(
    text: String,
    paddingValues: PaddingValues = PaddingValues(top = 16.dp, bottom = 2.dp),
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(paddingValues),
    )
}

@Composable
private fun MediaInfoText(
    title: String,
    subText: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        Text(text = "$title: ", style = MaterialTheme.typography.titleSmall)
        Text(text = subText)
    }
}
