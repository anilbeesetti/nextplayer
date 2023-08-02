package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.model.Directory
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.CancelButton
import dev.anilbeesetti.nextplayer.core.ui.components.DoneButton
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.FoldersState
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.VideosState
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.media.CIRCULAR_PROGRESS_INDICATOR_TEST_TAG
import kotlinx.coroutines.launch

@Composable
fun MediaLazyList(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: LazyListScope.() -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 10.dp),
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = verticalArrangement,
        content = content
    )
}

@Composable
fun CenterCircularProgressBar() {
    CircularProgressIndicator(
        modifier = Modifier
            .testTag(CIRCULAR_PROGRESS_INDICATOR_TEST_TAG)
    )
}

@Composable
fun NoVideosFound() {
    Text(
        text = stringResource(id = R.string.no_videos_found),
        style = MaterialTheme.typography.titleLarge
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VideosListFromState(
    videosState: VideosState,
    onVideoClick: (Uri) -> Unit,
    onDeleteVideoClick: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var showMediaActionsFor: Video? by rememberSaveable { mutableStateOf(null) }
    var deleteAction: Video? by rememberSaveable { mutableStateOf(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    when (videosState) {
        VideosState.Loading -> CenterCircularProgressBar()
        is VideosState.Success -> if (videosState.data.isEmpty()) {
            NoVideosFound()
        } else {
            MediaLazyList {
                items(videosState.data, key = { it.path }) {
                    VideoItem(
                        video = it,
                        modifier = Modifier.combinedClickable(
                            onClick = { onVideoClick(Uri.parse(it.uriString)) },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showMediaActionsFor = it
                            }
                        ),
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
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FoldersListFromState(
    foldersState: FoldersState,
    onFolderClick: (String) -> Unit,
    onDeleteFolderClick: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var showDirectoryActionsFor: Directory? by rememberSaveable { mutableStateOf(null) }
    var deleteAction: Directory? by rememberSaveable { mutableStateOf(null) }
    val scope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    when (foldersState) {
        FoldersState.Loading -> CenterCircularProgressBar()
        is FoldersState.Success -> if (foldersState.data.isEmpty()) {
            NoVideosFound()
        } else {
            MediaLazyList {
                items(foldersState.data, key = { it.path }) {
                    FolderItem(
                        directory = it,
                        modifier = Modifier.combinedClickable(
                            onClick = { onFolderClick(it.path) },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showDirectoryActionsFor = it
                            }
                        ),
                    )
                }
            }
        }
    }

    showDirectoryActionsFor?.let {
        OptionsBottomSheet(
            title = it.name,
            onDismiss = { showDirectoryActionsFor = null }
        ) {
            BottomSheetItem(
                text = stringResource(R.string.delete),
                icon = NextIcons.Delete,
                onClick = {
                    deleteAction = it
                    scope.launch { bottomSheetState.hide() }.invokeOnCompletion {
                        if (!bottomSheetState.isVisible) showDirectoryActionsFor = null
                    }
                }
            )
        }
    }

    deleteAction?.let {
        DeleteConfirmationDialog(
            subText = stringResource(R.string.delete_folder),
            onCancel = { deleteAction = null },
            onConfirm = {
                onDeleteFolderClick(it.path)
                deleteAction = null
            },
            fileNames = listOf(it.name)
        )
    }
}

@Composable
fun DeleteConfirmationDialog(
    subText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    fileNames: List<String>,
    modifier: Modifier = Modifier
) {
    NextDialog(
        onDismissRequest = onCancel,
        title = { Text(text = stringResource(R.string.delete), modifier = Modifier.fillMaxWidth()) },
        confirmButton = { DoneButton(onClick = onConfirm) },
        dismissButton = { CancelButton(onClick = onCancel) },
        modifier = modifier,
        content = {
            Text(
                text = subText,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(20.dp))
            LazyColumn {
                items(fileNames) {
                    Text(
                        text = it,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    )
}


@Preview
@Composable
fun DeleteDialogPreview() {
    DeleteConfirmationDialog(
        subText = "The following files will be deleted permanently",
        onConfirm = { /*TODO*/ },
        onCancel = { /*TODO*/ },
        fileNames = listOf("Harry potter 1", "Harry potter 2", "Harry potter 3", "Harry potter 4")
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(20.dp))
        content()
    }
}

@Composable
fun BottomSheetItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        leadingContent = { Icon(imageVector = icon, contentDescription = null) },
        headlineContent = { Text(text = text) },
        modifier = modifier.clickable(onClick = onClick)
    )
}
