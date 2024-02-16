package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.common.Utils
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.VideosState
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.media.MediaPickerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VideosView(
    videosState: VideosState,
    preferences: ApplicationPreferences,
    onVideoClick: (Uri) -> Unit,
    onDeleteVideoClick: (List<String>) -> Unit,
    toggleMultiSelect: () -> Unit,
    disableMultiSelect: Boolean,
    totalVideos: (Int) -> Unit,
    onVideoLoaded: (Uri) -> Unit = {},
    viewModel: MediaPickerViewModel?
) {
    val haptic = LocalHapticFeedback.current
    var showMediaActionsFor: Video? by rememberSaveable { mutableStateOf(null) }
    var deleteAction: Video? by rememberSaveable { mutableStateOf(null) }
    var showInfoAction: Video? by rememberSaveable { mutableStateOf(null) }
    var multiSelect by rememberSaveable { mutableStateOf(false) }

    if (disableMultiSelect) {
        multiSelect = false
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    when (videosState) {
        VideosState.Loading -> CenterCircularProgressBar()
        is VideosState.Success -> if (videosState.data.isEmpty()) {
            NoVideosFound()
        } else {
            totalVideos(videosState.data.size)
            MediaLazyList {
                if (disableMultiSelect) {
                    viewModel?.videoTracks = videosState.data.map { it.copy() }
                }

                items(viewModel?.videoTracks ?: videosState.data, key = { it.path }) { video ->
                    LaunchedEffect(Unit) {
                        onVideoLoaded(Uri.parse(video.uriString))
                    }
                    VideoItem(
                        video = video,
                        preferences = preferences,
                        isSelected = video.isSelected,
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                if (multiSelect) {
                                    video.isSelected = !video.isSelected
                                    viewModel?.let {
                                        toggleSelection(video, it)
                                    }
                                } else {
                                    onVideoClick(Uri.parse(video.uriString))
                                }
                            },
                            onLongClick = {
                                if (!multiSelect) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showMediaActionsFor = video
                                }
                            }
                        )
                    )
                }
            }
        }
    }

    showMediaActionsFor?.let {
        OptionsBottomSheet(
            title = it.displayName,
            onDismiss = { showMediaActionsFor = null }
        ) {
            BottomSheetItem(
                text = stringResource(R.string.delete),
                icon = NextIcons.Delete,
                onClick = {
                    deleteAction = it
                    scope.launch { bottomSheetState.hide() }.invokeOnCompletion {
                        if (!bottomSheetState.isVisible) showMediaActionsFor = null
                    }
                }
            )
            BottomSheetItem(
                text = stringResource(R.string.share),
                icon = NextIcons.Share,
                onClick = {
                    val mediaStoreUri = Uri.parse(it.uriString)
                    val intent = Intent.createChooser(
                        Intent().apply {
                            type = "video/*"
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_STREAM, mediaStoreUri)
                        },
                        null
                    )
                    context.startActivity(intent)
                    scope.launch { bottomSheetState.hide() }.invokeOnCompletion {
                        if (!bottomSheetState.isVisible) showMediaActionsFor = null
                    }
                }
            )
            BottomSheetItem(
                text = stringResource(R.string.properties),
                icon = NextIcons.Info,
                onClick = {
                    showInfoAction = it
                    scope.launch { bottomSheetState.hide() }.invokeOnCompletion {
                        if (!bottomSheetState.isVisible) showMediaActionsFor = null
                    }
                }
            )
            BottomSheetItem(
                text = stringResource(R.string.multi_select),
                icon = NextIcons.MultiSelect,
                onClick = {
                    multiSelect = true
                    toggleMultiSelect()
                    scope.launch { bottomSheetState.hide() }.invokeOnCompletion {
                        if (!bottomSheetState.isVisible) showMediaActionsFor = null
                    }
                }
            )
        }
    }

    deleteAction?.let {
        DeleteConfirmationDialog(
            subText = stringResource(id = R.string.delete_file),
            onCancel = { deleteAction = null },
            onConfirm = {
                onDeleteVideoClick(listOf(it.uriString))
                deleteAction = null
            },
            fileNames = listOf(it.nameWithExtension)
        )
    }

    showInfoAction?.let {
        ShowVideoInfoDialog(
            video = it,
            onDismiss = { showInfoAction = null }
        )
    }
}

private fun toggleSelection(video: Video, viewModel: MediaPickerViewModel) {
    if (video.isSelected) {
        viewModel.addToSelectedTracks(video)
    } else {
        viewModel.removeFromSelectedTracks(video)
    }
}

@Composable
fun ShowVideoInfoDialog(
    video: Video,
    onDismiss: () -> Unit
) {
    NextDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = video.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        content = {
            Divider()
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                MediaInfoTitle(text = stringResource(R.string.file))
                MediaInfoText(title = stringResource(id = R.string.file), subText = video.nameWithExtension)
                MediaInfoText(title = stringResource(id = R.string.location), subText = video.parentPath)
                MediaInfoText(title = stringResource(id = R.string.size), subText = video.formattedFileSize)
                MediaInfoText(title = stringResource(id = R.string.duration), subText = video.formattedDuration)
                video.format?.let { MediaInfoText(title = stringResource(id = R.string.format), subText = it) }
                video.videoStream?.let { videoStream ->
                    MediaInfoTitle(text = stringResource(id = R.string.video_track))
                    videoStream.title?.let { MediaInfoText(title = stringResource(id = R.string.title), subText = it) }
                    MediaInfoText(title = stringResource(id = R.string.codec), subText = videoStream.codecName)
                    MediaInfoText(title = stringResource(id = R.string.resolution), subText = "${videoStream.frameWidth} x ${videoStream.frameHeight}")
                    MediaInfoText(title = stringResource(id = R.string.frame_rate), subText = videoStream.frameRate.toInt().toString())
                    Utils.formatBitrate(videoStream.bitRate)?.let { MediaInfoText(title = stringResource(id = R.string.bitrate), subText = it) }
                }
                video.audioStreams.forEachIndexed { index, audioStream ->
                    MediaInfoTitle(text = "${stringResource(id = R.string.audio_track)} #${index + 1}")
                    audioStream.title?.let { MediaInfoText(title = stringResource(id = R.string.title), subText = it) }
                    MediaInfoText(title = stringResource(id = R.string.codec), subText = audioStream.codecName)
                    MediaInfoText(title = stringResource(id = R.string.sample_rate), subText = "${audioStream.sampleRate} Hz")
                    MediaInfoText(title = stringResource(id = R.string.sample_format), subText = audioStream.sampleFormat.toString())
                    Utils.formatBitrate(audioStream.bitRate)?.let { MediaInfoText(title = stringResource(id = R.string.bitrate), subText = it) }
                    MediaInfoText(title = stringResource(id = R.string.channels), subText = audioStream.channelLayout ?: audioStream.channels.toString())
                    Utils.formatLanguage(audioStream.language)?.let { MediaInfoText(title = stringResource(id = R.string.language), subText = it) }
                }
                video.subtitleStreams.forEachIndexed { index, subtitleStream ->
                    MediaInfoTitle(text = "${stringResource(id = R.string.subtitle_track)} #${index + 1}")
                    subtitleStream.title?.let { MediaInfoText(title = stringResource(id = R.string.title), subText = it) }
                    MediaInfoText(title = stringResource(id = R.string.codec), subText = subtitleStream.codecName)
                    Utils.formatLanguage(subtitleStream.language)?.let { MediaInfoText(title = stringResource(id = R.string.language), subText = it) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.okay))
            }
        }
    )
}

@Composable
fun MediaInfoTitle(
    text: String,
    paddingValues: PaddingValues = PaddingValues(top = 16.dp, bottom = 2.dp)
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(paddingValues)
    )
}

@Composable
fun MediaInfoText(
    title: String,
    subText: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        Text(text = "$title: ", style = MaterialTheme.typography.titleSmall)
        Text(text = subText)
    }
}
