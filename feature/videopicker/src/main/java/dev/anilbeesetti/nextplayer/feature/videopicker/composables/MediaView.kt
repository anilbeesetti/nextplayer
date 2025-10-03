package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.common.Utils
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.MediaLayoutMode
import dev.anilbeesetti.nextplayer.core.model.MediaViewMode
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.CancelButton
import dev.anilbeesetti.nextplayer.core.ui.components.DoneButton
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaView(
    isLoading: Boolean,
    rootFolder: Folder?,
    preferences: ApplicationPreferences,
    onFolderClick: (String) -> Unit,
    onDeleteFolderClick: (Folder) -> Unit,
    onVideoClick: (Uri) -> Unit,
    onRenameVideoClick: (Uri, String) -> Unit,
    onDeleteVideoClick: (String) -> Unit,
    onVideoLoaded: (Uri) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var showFolderActionsFor: Folder? by rememberSaveable { mutableStateOf(null) }
    var deleteFolderAction: Folder? by rememberSaveable { mutableStateOf(null) }
    val scope = rememberCoroutineScope()

    var showMediaActionsFor: Video? by rememberSaveable { mutableStateOf(null) }
    var deleteAction: Video? by rememberSaveable { mutableStateOf(null) }
    var renameAction: Video? by rememberSaveable { mutableStateOf(null) }
    var showInfoAction: Video? by rememberSaveable { mutableStateOf(null) }

    val context = LocalContext.current
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val primaryColor = MaterialTheme.colorScheme.primary

    if (isLoading) {
        CenterCircularProgressBar()
    } else {
        val folderMinWidth = 90.dp
        val videoMinWidth = 130.dp
        BoxWithConstraints {
            val contentHorizontalPadding = when (preferences.mediaLayoutMode) {
                MediaLayoutMode.LIST -> if (preferences.isTvLayout) 16.dp else 0.dp
                MediaLayoutMode.GRID -> 16.dp
            }
            val itemSpacing = when (preferences.mediaLayoutMode) {
                MediaLayoutMode.LIST -> 0.dp
                MediaLayoutMode.GRID -> 16.dp
            }
            val maxWidth = this.maxWidth - (contentHorizontalPadding * 2) - itemSpacing
            val maxFolders = (maxWidth / folderMinWidth).toInt()
            val maxVideos = (maxWidth / videoMinWidth).toInt()
            val spans = when (preferences.mediaLayoutMode) {
                MediaLayoutMode.LIST -> 1
                MediaLayoutMode.GRID -> lcm(maxFolders, maxVideos)
            }

            val singleFolderSpan = when (preferences.mediaLayoutMode) {
                MediaLayoutMode.LIST -> 1
                MediaLayoutMode.GRID -> if (preferences.isTvLayout) preferences.folderGridSpanForTv else spans / maxFolders
            }
            val singleVideoSpan = when (preferences.mediaLayoutMode) {
                MediaLayoutMode.LIST -> 1
                MediaLayoutMode.GRID -> if (preferences.isTvLayout) preferences.videoGridSpanForTv else spans / maxVideos
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(spans),
                contentPadding = PaddingValues(horizontal = contentHorizontalPadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(itemSpacing),
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            ) {
                if (rootFolder == null || rootFolder.folderList.isEmpty() && rootFolder.mediaList.isEmpty()) {
                    item(
                        span = { GridItemSpan(maxLineSpan) },
                    ) { NoVideosFound() }
                    return@LazyVerticalGrid
                }

                if (preferences.mediaViewMode == MediaViewMode.FOLDER_TREE && rootFolder.folderList.isNotEmpty()) {
                    item(
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        SectionTitle(title = stringResource(id = R.string.folders))
                    }
                }
                items(
                    items = rootFolder.folderList,
                    key = { it.path },
                    span = { GridItemSpan(singleFolderSpan) },
                ) { folder ->
                    if (!preferences.isTvLayout) {
                        FolderItem(
                            folder = folder,
                            isRecentlyPlayedFolder = rootFolder.isRecentlyPlayedVideo(folder.recentlyPlayedVideo),
                            preferences = preferences,
                            modifier = Modifier.combinedClickable(
                                onClick = { onFolderClick(folder.path) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showFolderActionsFor = folder
                                },
                            ),
                        )
                    } else {
                        var borderColor by remember { mutableStateOf(Color.Transparent) }
                        FolderItem(
                            folder = folder,
                            isRecentlyPlayedFolder = rootFolder.isRecentlyPlayedVideo(folder.recentlyPlayedVideo),
                            preferences = preferences,
                            modifier = Modifier
                                .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(10.dp))
                                .onFocusChanged { focusState ->
                                    borderColor = if (focusState.isFocused) {
                                        primaryColor
                                    } else {
                                        Color.Transparent
                                    }
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onFolderClick(folder.path) },
                                )
                        )
                    }
                }

                if (preferences.mediaViewMode == MediaViewMode.FOLDER_TREE && rootFolder.folderList.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(modifier = Modifier.size(12.dp))
                    }
                }

                if (preferences.mediaViewMode == MediaViewMode.FOLDER_TREE && rootFolder.mediaList.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionTitle(title = stringResource(id = R.string.videos))
                    }
                }
                items(
                    items = rootFolder.mediaList,
                    key = { it.path },
                    span = { GridItemSpan(singleVideoSpan) },
                ) { video ->
                    LaunchedEffect(Unit) {
                        onVideoLoaded(Uri.parse(video.uriString))
                    }
                    if (!preferences.isTvLayout) {
                        VideoItem(
                            video = video,
                            preferences = preferences,
                            isRecentlyPlayedVideo = rootFolder.isRecentlyPlayedVideo(video),
                            modifier = Modifier.combinedClickable(
                                onClick = { onVideoClick(Uri.parse(video.uriString)) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showMediaActionsFor = video
                                },
                            ),
                        )
                    } else {
                        var borderColor by remember { mutableStateOf(Color.Transparent) }
                        VideoItem(
                            video = video,
                            preferences = preferences,
                            isRecentlyPlayedVideo = rootFolder.isRecentlyPlayedVideo(video),
                            modifier = Modifier
                                .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
                                .onFocusChanged { focusState ->
                                    borderColor = if (focusState.isFocused) {
                                        primaryColor
                                    } else {
                                        Color.Transparent
                                    }
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onVideoClick(video.uriString.toUri()) },
                                )
                        )
                    }
                }
            }
        }
    }

    showFolderActionsFor?.let {
        OptionsBottomSheet(
            title = it.name,
            onDismiss = { showFolderActionsFor = null },
        ) {
            BottomSheetItem(
                text = stringResource(R.string.delete),
                icon = NextIcons.Delete,
                onClick = {
                    deleteFolderAction = it
                    scope.launch { bottomSheetState.hide() }.invokeOnCompletion {
                        if (!bottomSheetState.isVisible) showFolderActionsFor = null
                    }
                },
            )
        }
    }

    deleteFolderAction?.let { folder ->
        DeleteConfirmationDialog(
            subText = stringResource(R.string.delete_folder),
            onCancel = { deleteFolderAction = null },
            onConfirm = {
                onDeleteFolderClick(folder)
                deleteFolderAction = null
            },
            fileNames = listOf(folder.name),
        )
    }

    showMediaActionsFor?.let {
        OptionsBottomSheet(
            title = it.nameWithExtension,
            onDismiss = { showMediaActionsFor = null },
        ) {
            BottomSheetItem(
                text = stringResource(R.string.rename),
                icon = NextIcons.Edit,
                onClick = {
                    renameAction = it
                    scope.launch { bottomSheetState.hide() }.invokeOnCompletion {
                        if (!bottomSheetState.isVisible) showMediaActionsFor = null
                    }
                },
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
                        null,
                    )
                    context.startActivity(intent)
                    scope.launch { bottomSheetState.hide() }.invokeOnCompletion {
                        if (!bottomSheetState.isVisible) showMediaActionsFor = null
                    }
                },
            )
            BottomSheetItem(
                text = stringResource(R.string.properties),
                icon = NextIcons.Info,
                onClick = {
                    showInfoAction = it
                    scope.launch { bottomSheetState.hide() }.invokeOnCompletion {
                        if (!bottomSheetState.isVisible) showMediaActionsFor = null
                    }
                },
            )
            BottomSheetItem(
                text = stringResource(R.string.delete),
                icon = NextIcons.Delete,
                onClick = {
                    deleteAction = it
                    scope.launch { bottomSheetState.hide() }.invokeOnCompletion {
                        if (!bottomSheetState.isVisible) showMediaActionsFor = null
                    }
                },
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
            fileNames = listOf(it.nameWithExtension),
        )
    }

    showInfoAction?.let {
        ShowVideoInfoDialog(
            video = it,
            onDismiss = { showInfoAction = null },
        )
    }

    renameAction?.let { video ->
        ShowRenameDialog(
            name = video.displayName,
            onDismiss = { renameAction = null },
            onDone = {
                onRenameVideoClick(
                    Uri.parse(video.uriString),
                    "$it.${video.nameWithExtension.substringAfterLast(".")}",
                )
                renameAction = null
            },
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 4.dp),
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun ShowRenameDialog(
    name: String,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    var mediaName by rememberSaveable { mutableStateOf(name) }
    val focusRequester = remember { FocusRequester() }
    NextDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_to)) },
        content = {
            OutlinedTextField(
                value = mediaName,
                onValueChange = { mediaName = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        },
        confirmButton = {
            DoneButton(
                enabled = mediaName.isNotBlank(),
                onClick = { onDone(mediaName) },
            )
        },
        dismissButton = { CancelButton(onClick = onDismiss) },
    )

    LaunchedEffect(key1 = Unit) {
        // To fix focus requester not initialized error on screen rotation
        delay(200.milliseconds)
        focusRequester.requestFocus()
    }
}

@Composable
fun ShowVideoInfoDialog(
    video: Video,
    onDismiss: () -> Unit,
) {
    NextDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = video.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
fun MediaInfoTitle(
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
fun MediaInfoText(
    title: String,
    subText: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        Text(text = "$title: ", style = MaterialTheme.typography.titleSmall)
        Text(text = subText)
    }
}

fun lcm(a: Int, b: Int): Int {
    return abs(a * b) / gcd(a, b)
}

fun gcd(a: Int, b: Int): Int {
    return if (b == 0) a else gcd(b, a % b)
}
