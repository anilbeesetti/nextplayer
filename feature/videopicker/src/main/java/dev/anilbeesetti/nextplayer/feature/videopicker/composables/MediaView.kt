package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.MediaViewMode
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import kotlinx.coroutines.launch

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

    if (isLoading) {
        CenterCircularProgressBar()
    } else {
        MediaLazyList {
            if (rootFolder == null || rootFolder.folderList.isEmpty() && rootFolder.mediaList.isEmpty()) {
                item { NoVideosFound() }
                return@MediaLazyList
            }

            if (preferences.mediaViewMode == MediaViewMode.FOLDER_TREE && rootFolder.folderList.isNotEmpty()) {
                item {
                    SectionTitle(title = stringResource(id = R.string.folders))
                }
            }
            items(rootFolder.folderList, key = { it.path }) { folder ->
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
            }

            if (preferences.mediaViewMode == MediaViewMode.FOLDER_TREE && rootFolder.folderList.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.size(12.dp))
                }
            }

            if (preferences.mediaViewMode == MediaViewMode.FOLDER_TREE && rootFolder.mediaList.isNotEmpty()) {
                item {
                    SectionTitle(title = stringResource(id = R.string.videos))
                }
            }
            items(rootFolder.mediaList, key = { it.path }) { video ->
                LaunchedEffect(Unit) {
                    onVideoLoaded(Uri.parse(video.uriString))
                }
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
