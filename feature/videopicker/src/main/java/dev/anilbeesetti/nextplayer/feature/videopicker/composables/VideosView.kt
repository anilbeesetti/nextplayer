package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.VideosState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VideosView(
    videosState: VideosState,
    preferences: ApplicationPreferences,
    onVideoClick: (Uri) -> Unit,
    onDeleteVideoClick: (String) -> Unit,
    onVideoLoaded: (Uri) -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    var showMediaActionsFor: Video? by rememberSaveable { mutableStateOf(null) }
    var deleteAction: Video? by rememberSaveable { mutableStateOf(null) }
    var showInfoAction: Video? by rememberSaveable { mutableStateOf(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    when (videosState) {
        VideosState.Loading -> CenterCircularProgressBar()
        is VideosState.Success -> if (videosState.data.isEmpty()) {
            NoVideosFound()
        } else {
            MediaLazyList {
                items(videosState.data, key = { it.path }) { video ->
                    LaunchedEffect(Unit) {
                        if (video.videoStream == null) {
                            onVideoLoaded(Uri.parse(video.uriString))
                        }
                    }
                    VideoItem(
                        video = video,
                        preferences = preferences,
                        modifier = Modifier.combinedClickable(
                            onClick = { onVideoClick(Uri.parse(video.uriString)) },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showMediaActionsFor = video
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
                text = "properties",
                icon = NextIcons.Info,
                onClick = {
                    showInfoAction = it
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
                onDeleteVideoClick(it.uriString)
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
                MediaInfoTitle(
                    text = "File",
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
                MediaInfoText(title = "File", subText = video.nameWithExtension)
                MediaInfoText(title = "Location", subText = video.parentPath)
                MediaInfoText(title = "Size", subText = video.formattedFileSize)
                MediaInfoText(title = "Format", subText = video.format.toString())
                video.videoStream?.let {
                    MediaInfoTitle(
                        text = "Video Track",
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    MediaInfoText(title = "Title", subText = it.title.toString())
                    MediaInfoText(title = "Codec", subText = it.codecName)
                    MediaInfoText(title = "Resolution", subText = "${it.frameWidth} x ${it.frameHeight}")
                    MediaInfoText(title = "Frame rate", subText = it.frameRate.toInt().toString())
                    MediaInfoText(title = "Bitrate", subText = it.bitRate.toString())
                }
                video.audioStreams.forEachIndexed { index, it ->
                    MediaInfoTitle(
                        text = "Audio Track #${index + 1}",
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    MediaInfoText(title = "Title", subText = it.title.toString())
                    MediaInfoText(title = "Codec", subText = it.codecName)
                    MediaInfoText(title = "Sample rate", subText = it.sampleRate.toString())
                    MediaInfoText(title = "Sample format", subText = it.sampleFormat.toString())
                    MediaInfoText(title = "Bitrate", subText = it.bitRate.toString())
                    MediaInfoText(title = "Channels", subText = it.channelLayout ?: it.channels.toString())
                }
                video.subtitleStreams.forEachIndexed { index, it ->
                    MediaInfoTitle(
                        text = "Subtitle Track #${index + 1}",
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    MediaInfoText(title = "Title", subText = it.title.toString())
                    MediaInfoText(title = "Codec", subText = it.codecName)
                    MediaInfoText(title = "Language", subText = it.language.toString())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Okay")
            }
        },
        dismissButton = { }
    )
}


@Composable
fun MediaInfoTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
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