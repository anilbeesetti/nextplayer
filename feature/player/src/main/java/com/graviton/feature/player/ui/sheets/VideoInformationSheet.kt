package com.graviton.feature.player.ui.sheets

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.graviton.core.common.Utils
import com.graviton.core.model.DecoderMode
import com.graviton.core.model.MediaInfo
import com.graviton.core.ui.R
import com.graviton.feature.player.decoder.PlaybackDiagnosticsSnapshot
import com.graviton.feature.player.extensions.nameRes
import com.graviton.feature.player.state.MediaPresentationState
import com.graviton.feature.player.state.durationFormatted
import com.graviton.feature.player.ui.OverlayView
import kotlin.math.roundToInt


/**
 * FILE / VIDEO / AUDIO / SUBTITLES / DECODER / PERFORMANCE for the item being played.
 *
 * Every value comes from the app's existing media pipeline - `MediaInfo` from the nextlib media
 * info builder, the live Media3 `Format`s, and the service's playback diagnostics. Anything the
 * pipeline has not reported renders as "Unknown" rather than being guessed at.
 */
@OptIn(UnstableApi::class)
@Composable
fun BoxScope.VideoInformationSheet(
    modifier: Modifier = Modifier,
    show: Boolean,
    isLoading: Boolean,
    mediaInfo: MediaInfo?,
    player: Player,
    mediaPresentationState: MediaPresentationState,
    diagnostics: PlaybackDiagnosticsSnapshot,
    decoderMode: DecoderMode,
    onDismiss: () -> Unit,
) {
    OverlayView(
        modifier = modifier,
        show = show,
        title = stringResource(R.string.video_information),
        onDismiss = onDismiss,
    ) {
        if (isLoading && mediaInfo == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
            return@OverlayView
        }

        val unknown = stringResource(R.string.unknown)
        // `Player` is a MediaController here, so the renderer-level videoFormat/audioFormat of
        // ExoPlayer are not reachable. The selected track's Format carries the same values and is
        // part of the session state the controller already receives.
        val videoFormat = player.selectedFormat(C.TRACK_TYPE_VIDEO)
        val audioFormat = player.selectedFormat(C.TRACK_TYPE_AUDIO)

        BoxWithConstraints {
            // A wide sheet (tablet, landscape, desktop-class window) gets two columns of rows so
            // the information does not stretch into unreadable full-width lines.
            val useTwoColumns = maxWidth >= 600.dp

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                val fileRows = buildList {
                    add(stringResource(R.string.title) to (mediaInfo?.video?.displayName ?: unknown))
                    add(stringResource(R.string.format) to (mediaInfo?.video?.format ?: unknown))
                    add(
                        stringResource(R.string.size) to
                            (mediaInfo?.video?.size?.takeIf { it > 0 }?.let { Utils.formatFileSize(it) } ?: unknown),
                    )
                    add(stringResource(R.string.duration) to mediaPresentationState.durationFormatted)
                    add(stringResource(R.string.path) to (mediaInfo?.video?.path ?: unknown))
                }
                SheetSectionTitle(text = stringResource(R.string.section_file))
                InfoRows(rows = fileRows, useTwoColumns = useTwoColumns)

                SheetSectionTitle(text = stringResource(R.string.section_video))
                val videoStream = mediaInfo?.videoStream
                if (videoStream == null && videoFormat == null) {
                    SheetInfoRow(
                        label = stringResource(R.string.video_track),
                        value = stringResource(R.string.no_video_track_in_file),
                    )
                } else {
                    val width = videoStream?.frameWidth ?: videoFormat?.width?.takeIf { it != Format.NO_VALUE }
                    val height = videoStream?.frameHeight ?: videoFormat?.height?.takeIf { it != Format.NO_VALUE }
                    val frameRate = videoStream?.frameRate?.takeIf { it > 0 }
                        ?: videoFormat?.frameRate?.takeIf { it > 0f }?.toDouble()
                    val bitRate = videoStream?.bitRate?.takeIf { it > 0 }
                        ?: videoFormat?.bitrate?.takeIf { it != Format.NO_VALUE }?.toLong()
                    InfoRows(
                        rows = listOf(
                            stringResource(R.string.codec) to
                                (videoStream?.codecName ?: videoFormat?.sampleMimeType ?: unknown),
                            stringResource(R.string.resolution) to
                                (if (width != null && height != null) "$width × $height" else unknown),
                            stringResource(R.string.frame_rate) to
                                (frameRate?.let { "%.2f fps".format(it) } ?: unknown),
                            stringResource(R.string.bitrate) to
                                (bitRate?.let { Utils.formatBitrate(it) } ?: unknown),
                        ),
                        useTwoColumns = useTwoColumns,
                        monospace = true,
                    )
                }

                SheetSectionTitle(text = stringResource(R.string.section_audio))
                val audioStreams = mediaInfo?.audioStreams.orEmpty()
                if (audioStreams.isEmpty() && audioFormat == null) {
                    SheetInfoRow(
                        label = stringResource(R.string.audio_track),
                        value = stringResource(R.string.no_audio_track_in_file),
                    )
                } else if (audioStreams.isEmpty()) {
                    InfoRows(
                        rows = listOf(
                            stringResource(R.string.codec) to (audioFormat?.sampleMimeType ?: unknown),
                            stringResource(R.string.sample_rate) to
                                (audioFormat?.sampleRate?.takeIf { it != Format.NO_VALUE }?.let { "$it Hz" } ?: unknown),
                            stringResource(R.string.channels) to
                                (audioFormat?.channelCount?.takeIf { it != Format.NO_VALUE }?.toString() ?: unknown),
                        ),
                        useTwoColumns = useTwoColumns,
                        monospace = true,
                    )
                } else {
                    audioStreams.forEachIndexed { index, stream ->
                        if (audioStreams.size > 1) {
                            SheetInfoRow(
                                label = stringResource(R.string.audio_track),
                                value = stream.title ?: "#${index + 1}",
                            )
                        }
                        InfoRows(
                            rows = listOfNotNull(
                                stringResource(R.string.codec) to stream.codecName,
                                stringResource(R.string.sample_rate) to
                                    (stream.sampleRate.takeIf { it > 0 }?.let { "$it Hz" } ?: unknown),
                                stringResource(R.string.channels) to
                                    (stream.channelLayout ?: stream.channels.takeIf { it > 0 }?.toString() ?: unknown),
                                stringResource(R.string.bitrate) to
                                    (stream.bitRate.takeIf { it > 0 }?.let { Utils.formatBitrate(it) } ?: unknown),
                                Utils.formatLanguage(stream.language)?.let { stringResource(R.string.language) to it },
                            ),
                            useTwoColumns = useTwoColumns,
                            monospace = true,
                        )
                    }
                }

                SheetSectionTitle(text = stringResource(R.string.section_subtitles))
                val subtitleStreams = mediaInfo?.subtitleStreams.orEmpty()
                if (subtitleStreams.isEmpty()) {
                    SheetInfoRow(
                        label = stringResource(R.string.subtitle),
                        value = stringResource(R.string.subtitle_track_none),
                    )
                } else {
                    subtitleStreams.forEachIndexed { index, stream ->
                        SheetInfoRow(
                            label = stream.title ?: "#${index + 1}",
                            value = listOfNotNull(
                                stream.codecName,
                                Utils.formatLanguage(stream.language),
                            ).joinToString(" · "),
                        )
                    }
                }

                SheetSectionTitle(text = stringResource(R.string.section_decoder))
                // A null kind means the platform has not reported one yet, which is not the same
                // thing as software decoding, so it stays "Unknown" rather than guessing.
                val decoderKind = when (diagnostics.isVideoDecoderHardware) {
                    true -> stringResource(R.string.decoder_mode_hardware)
                    false -> stringResource(R.string.decoder_mode_software)
                    null -> unknown
                }
                InfoRows(
                    rows = listOf(
                        stringResource(R.string.decoder_mode) to stringResource(decoderMode.nameRes()),
                        stringResource(R.string.video_decoder) to
                            (
                                diagnostics.videoDecoderName?.let { "$it ($decoderKind)" }
                                    ?: unknown
                                ),
                        stringResource(R.string.audio_decoder) to (diagnostics.audioDecoderName ?: unknown),
                    ),
                    useTwoColumns = useTwoColumns,
                )

                SheetSectionTitle(text = stringResource(R.string.section_performance))
                val bufferedMs = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)
                InfoRows(
                    rows = listOf(
                        stringResource(R.string.playback_state) to when (player.playbackState) {
                            Player.STATE_BUFFERING -> stringResource(R.string.state_buffering)
                            Player.STATE_READY -> if (player.isPlaying) {
                                stringResource(R.string.state_playing)
                            } else {
                                stringResource(R.string.state_paused)
                            }
                            Player.STATE_ENDED -> stringResource(R.string.state_ended)
                            else -> stringResource(R.string.state_idle)
                        },
                        stringResource(R.string.dropped_frames) to diagnostics.droppedFrames.toString(),
                        stringResource(R.string.buffered_position) to "${(bufferedMs / 1000f).roundToInt()} s",
                        stringResource(R.string.playback_speed) to "%.2f×".format(player.playbackParameters.speed),
                    ),
                    useTwoColumns = useTwoColumns,
                    monospace = true,
                )
            }
        }
    }
}

/**
 * Lays the label/value rows out in one column on phones and two on wide windows.
 */
@Composable
private fun InfoRows(
    rows: List<Pair<String, String>>,
    useTwoColumns: Boolean,
    monospace: Boolean = false,
) {
    if (!useTwoColumns) {
        rows.forEach { (label, value) ->
            SheetInfoRow(label = label, value = value, monospace = monospace)
        }
        return
    }

    rows.chunked(2).forEach { pair ->
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            pair.forEach { (label, value) ->
                SheetInfoRow(
                    label = label,
                    value = value,
                    monospace = monospace,
                    modifier = Modifier.weight(1f),
                )
            }
            // Keeps a trailing odd row aligned with the first column instead of centring it.
            if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/** The Format of the currently selected track of [trackType], or null when nothing is selected. */
@OptIn(UnstableApi::class)
private fun Player.selectedFormat(trackType: @C.TrackType Int): Format? =
    currentTracks.groups
        .firstOrNull { it.type == trackType && it.isSelected }
        ?.let { group ->
            (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let(group::getTrackFormat)
        }
