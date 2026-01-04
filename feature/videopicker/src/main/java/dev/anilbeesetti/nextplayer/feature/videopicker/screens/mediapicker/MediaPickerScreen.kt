package dev.anilbeesetti.nextplayer.feature.videopicker.screens.mediapicker

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import dev.anilbeesetti.nextplayer.core.common.storagePermission
import dev.anilbeesetti.nextplayer.core.media.services.MediaService
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.MediaLayoutMode
import dev.anilbeesetti.nextplayer.core.model.MediaViewMode
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.components.CancelButton
import dev.anilbeesetti.nextplayer.core.ui.components.DoneButton
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.composables.PermissionMissingView
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.extensions.copy
import dev.anilbeesetti.nextplayer.core.ui.preview.DayNightPreview
import dev.anilbeesetti.nextplayer.core.ui.preview.VideoPickerPreviewParameterProvider
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.CenterCircularProgressBar
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.MediaView
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.QuickSettingsDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.RenameDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.TextIconToggleButton
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.VideoInfoDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.state.SelectedFolder
import dev.anilbeesetti.nextplayer.feature.videopicker.state.SelectedVideo
import dev.anilbeesetti.nextplayer.feature.videopicker.state.rememberSelectionManager

@Composable
fun MediaPickerRoute(
    viewModel: MediaPickerViewModel = hiltViewModel(),
    onPlayVideos: (uris: List<Uri>) -> Unit,
    onFolderClick: (folderPath: String) -> Unit,
    onSettingsClick: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MediaPickerScreen(
        uiState = uiState,
        onPlayVideos = onPlayVideos,
        onNavigateUp = onNavigateUp,
        onFolderClick = onFolderClick,
        onSettingsClick = onSettingsClick,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalPermissionsApi::class)
@Composable
internal fun MediaPickerScreen(
    uiState: MediaPickerUiState,
    onNavigateUp: () -> Unit = {},
    onPlayVideos: (List<Uri>) -> Unit = {},
    onFolderClick: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onEvent: (MediaPickerUiEvent) -> Unit = {},
) {
    val selectionManager = rememberSelectionManager()
    val permissionState = rememberPermissionState(permission = storagePermission)
    val lazyGridState = rememberLazyGridState()
    val selectVideoFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { it?.let { onPlayVideos(listOf(it)) } },
    )

    var isFabExpanded by rememberSaveable { mutableStateOf(false) }
    var showQuickSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showUrlDialog by rememberSaveable { mutableStateOf(false) }

    var showRenameActionFor: Video? by rememberSaveable { mutableStateOf(null) }
    var showInfoActionFor: Video? by rememberSaveable { mutableStateOf(null) }
    var showDeleteVideosConfirmation by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = (uiState.folderName ?: stringResource(R.string.app_name)).takeIf { !selectionManager.isInSelectionMode } ?: "",
                fontWeight = FontWeight.Bold.takeIf { uiState.folderName == null },
                navigationIcon = {
                    if (selectionManager.isInSelectionMode) {
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .clickable { selectionManager.clearSelection() }
                                .padding(8.dp)
                                .padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = NextIcons.Close,
                                contentDescription = stringResource(id = R.string.navigate_up),
                            )
                            Text(
                                text = (selectionManager.selectedFolders.size + selectionManager.selectedVideos.size).toString(),
                                style = MaterialTheme.typography.bodyMediumEmphasized,
                            )
                        }
                    } else if (uiState.folderName != null) {
                        FilledTonalIconButton(onClick = onNavigateUp) {
                            Icon(
                                imageVector = NextIcons.ArrowBack,
                                contentDescription = stringResource(id = R.string.navigate_up),
                            )
                        }
                    }
                },
                actions = {
                    if (selectionManager.isInSelectionMode) return@NextTopAppBar
                    IconButton(onClick = { showQuickSettingsDialog = true }) {
                        Icon(
                            imageVector = NextIcons.DashBoard,
                            contentDescription = stringResource(id = R.string.menu),
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = NextIcons.Settings,
                            contentDescription = stringResource(id = R.string.settings),
                        )
                    }
                },
            )
        },
        bottomBar = {
            SelectionActionsSheet(
                show = selectionManager.isInSelectionMode,
                showRenameAction = selectionManager.isSingleVideoSelected,
                showInfoAction = selectionManager.isSingleVideoSelected,
                onPlayAction = {
                    val videoUris = selectionManager.allSelectedVideos.map { it.uriString.toUri() }
                    onPlayVideos(videoUris)
                    selectionManager.clearSelection()
                },
                onRenameAction = {
                    val selectedVideo = selectionManager.selectedVideos.firstOrNull() ?: return@SelectionActionsSheet
                    val video = (uiState.mediaDataState as? DataState.Success)?.value?.mediaList
                        ?.find { it.uriString == selectedVideo.uriString } ?: return@SelectionActionsSheet
                    showRenameActionFor = video
                },
                onInfoAction = {
                    val selectedVideo = selectionManager.selectedVideos.firstOrNull() ?: return@SelectionActionsSheet
                    val video = (uiState.mediaDataState as? DataState.Success)?.value?.mediaList
                        ?.find { it.uriString == selectedVideo.uriString } ?: return@SelectionActionsSheet
                    showInfoActionFor = video
                    selectionManager.clearSelection()
                },
                onShareAction = {
                    onEvent(MediaPickerUiEvent.ShareVideos(selectionManager.allSelectedVideos.map { it.uriString }))
                },
                onDeleteAction = {
                    if (MediaService.willSystemAsksForDeleteConfirmation()) {
                        onEvent(MediaPickerUiEvent.DeleteVideos(selectionManager.allSelectedVideos.map { it.uriString }))
                    } else {
                        showDeleteVideosConfirmation = true
                    }
                },
            )
        },
        floatingActionButton = {
            if (!uiState.preferences.showFloatingPlayButton) return@Scaffold
            if (selectionManager.isInSelectionMode) return@Scaffold

            FloatingActionButtonMenu(
                expanded = isFabExpanded,
                button = {
                    ToggleFloatingActionButton(
                        checked = isFabExpanded,
                        onCheckedChange = { isFabExpanded = !isFabExpanded },
                    ) {
                        val icon by remember {
                            derivedStateOf {
                                if (checkedProgress > 0.5f) NextIcons.Close else NextIcons.Play
                            }
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.animateIcon(checkedProgress = { checkedProgress }),
                        )
                    }
                },
            ) {
                FloatingActionButtonMenuItem(
                    onClick = {
                        isFabExpanded = false
                        showUrlDialog = true
                    },
                    icon = {
                        Icon(
                            imageVector = NextIcons.Link,
                            contentDescription = null,
                        )
                    },
                    text = {
                        Text(text = stringResource(id = R.string.open_network_stream))
                    },
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        isFabExpanded = false
                        selectVideoFileLauncher.launch("video/*")
                    },
                    icon = {
                        Icon(
                            imageVector = NextIcons.FileOpen,
                            contentDescription = null,
                        )
                    },
                    text = {
                        Text(text = stringResource(id = R.string.open_local_video))
                    },
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        isFabExpanded = false
                        val folder = (uiState.mediaDataState as? DataState.Success)?.value ?: return@FloatingActionButtonMenuItem
                        val videoToPlay = folder.recentlyPlayedVideo ?: folder.firstVideo ?: return@FloatingActionButtonMenuItem
                        onPlayVideos(listOf(videoToPlay.uriString.toUri()))
                    },
                    icon = {
                        Icon(
                            imageVector = NextIcons.History,
                            contentDescription = null,
                        )
                    },
                    text = {
                        Text(text = stringResource(id = R.string.recently_played))
                    },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { scaffoldPadding ->
        when (uiState.mediaDataState) {
            is DataState.Error -> {
            }

            is DataState.Loading -> {
                CenterCircularProgressBar(modifier = Modifier.padding(scaffoldPadding))
            }

            is DataState.Success -> {
                PullToRefreshBox(
                    modifier = Modifier.padding(top = scaffoldPadding.calculateTopPadding()),
                    isRefreshing = uiState.refreshing,
                    onRefresh = { onEvent(MediaPickerUiEvent.Refresh) },
                ) {
                    PermissionMissingView(
                        isGranted = permissionState.status.isGranted,
                        showRationale = permissionState.status.shouldShowRationale,
                        permission = permissionState.permission,
                        launchPermissionRequest = { permissionState.launchPermissionRequest() },
                    ) {
                        MediaView(
                            rootFolder = uiState.mediaDataState.value,
                            preferences = uiState.preferences,
                            onFolderClick = onFolderClick,
                            onVideoClick = { onPlayVideos(listOf(it)) },
                            selectionManager = selectionManager,
                            lazyGridState = lazyGridState,
                            contentPadding = scaffoldPadding.copy(top = 0.dp),
                            onVideoLoaded = { onEvent(MediaPickerUiEvent.AddToSync(it)) },
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(lazyGridState.isScrollInProgress) {
        if (isFabExpanded && lazyGridState.isScrollInProgress) {
            isFabExpanded = false
        }
    }

    LaunchedEffect(selectionManager.isInSelectionMode) {
        if (selectionManager.isInSelectionMode) {
            isFabExpanded = false
        }
    }

    BackHandler(enabled = isFabExpanded) {
        isFabExpanded = false
    }

    BackHandler(enabled = selectionManager.isInSelectionMode) {
        selectionManager.clearSelection()
    }

    if (showQuickSettingsDialog) {
        QuickSettingsDialog(
            applicationPreferences = uiState.preferences,
            onDismiss = { showQuickSettingsDialog = false },
            updatePreferences = { onEvent(MediaPickerUiEvent.UpdateMenu(it)) },
        )
    }

    if (showUrlDialog) {
        NetworkUrlDialog(
            onDismiss = { showUrlDialog = false },
            onDone = { onPlayVideos(listOf(it.toUri())) },
        )
    }

    showRenameActionFor?.let { video ->
        RenameDialog(
            name = video.displayName,
            onDismiss = { showRenameActionFor = null },
            onDone = {
                onEvent(MediaPickerUiEvent.RenameVideo(video.uriString.toUri(), it))
                showRenameActionFor = null
                selectionManager.clearSelection()
            },
        )
    }

    showInfoActionFor?.let { video ->
        VideoInfoDialog(
            video = video,
            onDismiss = { showInfoActionFor = null },
        )
    }

    if (showDeleteVideosConfirmation) {
        DeleteConfirmationDialog(
            selectedVideos = selectionManager.selectedVideos,
            selectedFolders = selectionManager.selectedFolders,
            onConfirm = {
                onEvent(MediaPickerUiEvent.DeleteVideos(selectionManager.allSelectedVideos.map { it.uriString }))
                selectionManager.clearSelection()
                showDeleteVideosConfirmation = false
            },
            onCancel = { showDeleteVideosConfirmation = false },
        )
    }
}

@Composable
private fun DeleteConfirmationDialog(
    modifier: Modifier = Modifier,
    selectedVideos: Set<SelectedVideo>,
    selectedFolders: Set<SelectedFolder>,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    NextDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = when {
                    selectedVideos.isEmpty() -> when (selectedFolders.size) {
                        1 -> stringResource(R.string.delete_one_folder)
                        else -> stringResource(R.string.delete_folders, selectedFolders.size)
                    }
                    selectedFolders.isEmpty() -> when (selectedVideos.size) {
                        1 -> stringResource(R.string.delete_one_video)
                        else -> stringResource(R.string.delete_videos, selectedVideos.size)
                    }
                    else -> stringResource(R.string.delete_items, selectedFolders.size + selectedVideos.size)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = modifier,
            ) {
                Text(text = stringResource(R.string.delete))
            }
        },
        dismissButton = { CancelButton(onClick = onCancel) },
        modifier = modifier,
        content = {
            Text(
                text = if ((selectedFolders.size + selectedVideos.size) == 1) {
                    stringResource(R.string.delete_item_info)
                } else {
                    stringResource(R.string.delete_items_info)
                },
                style = MaterialTheme.typography.titleSmall,
            )
        },
    )
}

@Composable
private fun NetworkUrlDialog(
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }
    NextDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.network_stream)) },
        content = {
            Text(text = stringResource(R.string.enter_a_network_url))
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = stringResource(R.string.example_url)) },
            )
        },
        confirmButton = {
            DoneButton(
                enabled = url.isNotBlank(),
                onClick = { onDone(url) },
            )
        },
        dismissButton = { CancelButton(onClick = onDismiss) },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SelectionActionsSheet(
    modifier: Modifier = Modifier,
    show: Boolean,
    showRenameAction: Boolean,
    showInfoAction: Boolean,
    onPlayAction: () -> Unit,
    onRenameAction: () -> Unit,
    onShareAction: () -> Unit,
    onInfoAction: () -> Unit,
    onDeleteAction: () -> Unit,
) {
    AnimatedVisibility(
        modifier = modifier,
        visible = show,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
        val shape = MaterialTheme.shapes.largeIncreased
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = shape,
                    )
                    .clip(shape)
                    .horizontalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(
                        horizontal = 8.dp,
                        vertical = 12.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SelectionAction(
                    imageVector = NextIcons.Play,
                    title = stringResource(R.string.play),
                    onClick = onPlayAction,
                )
                if (showRenameAction) {
                    SelectionAction(
                        imageVector = NextIcons.Edit,
                        title = stringResource(R.string.rename),
                        onClick = onRenameAction,
                    )
                }
                SelectionAction(
                    imageVector = NextIcons.Share,
                    title = stringResource(R.string.share),
                    onClick = onShareAction,
                )
                if (showInfoAction) {
                    SelectionAction(
                        imageVector = NextIcons.Info,
                        title = stringResource(id = R.string.info),
                        onClick = onInfoAction,
                    )
                }
                SelectionAction(
                    imageVector = NextIcons.Delete,
                    title = stringResource(id = R.string.delete),
                    onClick = onDeleteAction,
                )
            }
        }
    }
}

@Composable
private fun SelectionAction(
    imageVector: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .defaultMinSize(
                minWidth = 75.dp,
                minHeight = 64.dp,
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(
                horizontal = 16.dp,
                vertical = 8.dp,
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = title,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = title,
            modifier = Modifier,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@PreviewScreenSizes
@PreviewLightDark
@Composable
private fun MediaPickerScreenPreview(
    @PreviewParameter(VideoPickerPreviewParameterProvider::class)
    videos: List<Video>,
) {
    NextPlayerTheme {
        MediaPickerScreen(
            uiState = MediaPickerUiState(
                folderName = null,
                mediaDataState = DataState.Success(
                    value = Folder(
                        name = "Root Folder",
                        path = "/root",
                        dateModified = System.currentTimeMillis(),
                        folderList = listOf(
                            Folder(name = "Folder 1", path = "/root/folder1", dateModified = System.currentTimeMillis()),
                            Folder(name = "Folder 2", path = "/root/folder2", dateModified = System.currentTimeMillis()),
                        ),
                        mediaList = videos,
                    ),
                ),
                preferences = ApplicationPreferences().copy(
                    mediaViewMode = MediaViewMode.FOLDER_TREE,
                    mediaLayoutMode = MediaLayoutMode.GRID,
                ),
            ),
        )
    }
}

@Preview
@Composable
private fun ButtonPreview() {
    Surface {
        TextIconToggleButton(
            text = "Title",
            icon = NextIcons.Title,
            onClick = {},
        )
    }
}

@DayNightPreview
@Composable
private fun MediaPickerNoVideosFoundPreview() {
    NextPlayerTheme {
        Surface {
            MediaPickerScreen(
                uiState = MediaPickerUiState(
                    folderName = null,
                    mediaDataState = DataState.Success(null),
                    preferences = ApplicationPreferences(),
                ),
            )
        }
    }
}

@DayNightPreview
@Composable
private fun MediaPickerLoadingPreview() {
    NextPlayerTheme {
        Surface {
            MediaPickerScreen(
                uiState = MediaPickerUiState(
                    folderName = null,
                    mediaDataState = DataState.Loading,
                    preferences = ApplicationPreferences(),
                ),
            )
        }
    }
}
