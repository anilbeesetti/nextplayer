package dev.anilbeesetti.nextplayer.feature.videopicker.screens.mediapicker

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.common.storagePermission
import dev.anilbeesetti.nextplayer.core.domain.MediaHolder
import dev.anilbeesetti.nextplayer.core.media.services.MediaOperationsService
import dev.anilbeesetti.nextplayer.core.media.services.TransferMode
import dev.anilbeesetti.nextplayer.core.media.services.TransferProgress
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.MediaLayoutMode
import dev.anilbeesetti.nextplayer.core.model.MediaViewMode
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.components.BindTopLevelBottomBarVisible
import dev.anilbeesetti.nextplayer.core.ui.components.BindTopLevelFab
import dev.anilbeesetti.nextplayer.core.ui.components.CancelButton
import dev.anilbeesetti.nextplayer.core.ui.components.LocalNavigationBottomPadding
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.TopLevelFabKey
import dev.anilbeesetti.nextplayer.core.ui.components.rememberRestorableFocusState
import dev.anilbeesetti.nextplayer.core.ui.components.thenIf
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.core.ui.composables.PermissionMissingView
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.extensions.copy
import dev.anilbeesetti.nextplayer.core.ui.preview.DayNightPreview
import dev.anilbeesetti.nextplayer.core.ui.preview.VideoPickerPreviewParameterProvider
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.CenterCircularProgressBar
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.MediaInfoDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.MediaView
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.NoVideosFound
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.QuickSettingsDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.RenameDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.SelectionAction
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.TextIconToggleButton
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.vault.PinDotsIndicator
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.vault.PinKeypad
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.vault.VaultBiometricSetupDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.vault.VaultProgressDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.vault.VAULT_PIN_LENGTH
import dev.anilbeesetti.nextplayer.feature.videopicker.state.SelectionItem
import dev.anilbeesetti.nextplayer.feature.videopicker.state.SelectionManager
import dev.anilbeesetti.nextplayer.feature.videopicker.state.rememberSelectionManager
import dev.anilbeesetti.nextplayer.feature.videopicker.state.toSelectedFolder
import dev.anilbeesetti.nextplayer.feature.videopicker.state.toSelectedVideo
import kotlin.math.roundToInt

@Composable
fun MediaPickerRoute(
    viewModel: MediaPickerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)

    MediaPickerScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalPermissionsApi::class,
)
@Composable
internal fun MediaPickerScreen(
    uiState: MediaPickerUiState,
    selectionManager: SelectionManager = rememberSelectionManager(),
    onAction: (MediaPickerAction) -> Unit = {},
) {
    val context = LocalContext.current
    val isTv = remember { context.isTelevision }
    val focusState = rememberRestorableFocusState()
    val hasMedia = (uiState.mediaDataState as? DataState.Success)?.value
        ?.let { it.folders.isNotEmpty() || it.videos.isNotEmpty() } == true
    val navigationBottomPadding = LocalNavigationBottomPadding.current

    // Re-enter the content region so its previously focused item is restored.
    val topBarDownModifier = if (isTv && hasMedia) {
        Modifier.focusProperties { down = focusState.requester }
    } else {
        Modifier
    }
    val permissionState = rememberPermissionState(permission = storagePermission)
    var wasPermissionGranted by remember { mutableStateOf(permissionState.status.isGranted) }

    LaunchedEffect(permissionState.status.isGranted) {
        val isPermissionGranted = permissionState.status.isGranted
        if (isPermissionGranted && !wasPermissionGranted) {
            onAction(MediaPickerAction.OnPermissionAccepted)
        }
        wasPermissionGranted = isPermissionGranted
    }
    val lazyGridState = rememberLazyGridState()
    val selectVideoFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> uri?.let { onAction(MediaPickerAction.OnPlayVideo(it)) } },
    )

    var showQuickSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showRenameActionFor: Video? by rememberSaveable { mutableStateOf(null) }
    var showDeleteVideosConfirmation by rememberSaveable { mutableStateOf(false) }

    val mediaHolder = (uiState.mediaDataState as? DataState.Success)?.value
    val onFabClick = {
        val selectedItem = uiState.recentlyPlayedVideo?.toSelectedVideo()
            ?: mediaHolder?.folders?.firstOrNull()?.toSelectedFolder()
            ?: mediaHolder?.videos?.firstOrNull()?.toSelectedVideo()

        selectedItem?.let { onAction(MediaPickerAction.PlaySelectedItems(setOf(selectedItem))) }
            ?: selectVideoFileLauncher.launch("video/*")
    }

    BindTopLevelBottomBarVisible(uiState.folderName != null || !selectionManager.isInSelectionMode)

    if (uiState.folderName == null) {
        BindTopLevelFab(
            key = TopLevelFabKey.MEDIA,
            icon = NextIcons.Play,
            onClick = onFabClick,
        )
    }

    val selectedItemsSize = selectionManager.selectionItems.size
    val totalItemsSize = (uiState.mediaDataState as? DataState.Success)?.value?.run { folders.size + videos.size } ?: 0

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = {
                    val titleText = (uiState.folderName ?: stringResource(R.string.app_name))
                        .takeIf { !selectionManager.isInSelectionMode } ?: ""
                    Text(
                        text = titleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold.takeIf { uiState.folderName == null },
                    )
                },
                navigationIcon = {
                    if (selectionManager.isInSelectionMode) {
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .clickable { selectionManager.exitSelectionMode() }
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
                                text = stringResource(R.string.m_n_selected, selectedItemsSize, totalItemsSize),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    } else if (uiState.folderName != null) {
                        FilledTonalIconButton(
                            onClick = { onAction(MediaPickerAction.OnNavigateUpClick) },
                            modifier = topBarDownModifier.tvFocusRing(isTv),
                        ) {
                            Icon(
                                imageVector = NextIcons.ArrowBack,
                                contentDescription = stringResource(id = R.string.navigate_up),
                            )
                        }
                    }
                },
                actions = {
                    if (selectionManager.isInSelectionMode) {
                        FilledTonalIconButton(
                            onClick = {
                                if (selectedItemsSize != totalItemsSize) {
                                    (uiState.mediaDataState as? DataState.Success)?.value?.let { folder ->
                                        folder.folders.forEach { selectionManager.selectFolder(it) }
                                        folder.videos.forEach { selectionManager.selectVideo(it) }
                                    }
                                } else {
                                    selectionManager.clearSelection()
                                }
                            },
                            modifier = topBarDownModifier.tvFocusRing(isTv),
                        ) {
                            Icon(
                                imageVector = if (selectedItemsSize != totalItemsSize) {
                                    NextIcons.SelectAll
                                } else {
                                    NextIcons.DeselectAll
                                },
                                contentDescription = if (selectedItemsSize != totalItemsSize) {
                                    stringResource(R.string.select_all)
                                } else {
                                    stringResource(R.string.deselect_all)
                                },
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { onAction(MediaPickerAction.OnSearchClick) },
                            modifier = topBarDownModifier.tvFocusRing(isTv),
                        ) {
                            Icon(
                                imageVector = NextIcons.Search,
                                contentDescription = stringResource(id = R.string.search),
                            )
                        }
                        IconButton(
                            onClick = { showQuickSettingsDialog = true },
                            modifier = topBarDownModifier.tvFocusRing(isTv),
                        ) {
                            Icon(
                                imageVector = NextIcons.DashBoard,
                                contentDescription = stringResource(id = R.string.menu),
                            )
                        }
                        IconButton(
                            onClick = { onAction(MediaPickerAction.OnSettingsClick) },
                            modifier = topBarDownModifier.tvFocusRing(isTv),
                        ) {
                            Icon(
                                imageVector = NextIcons.Settings,
                                contentDescription = stringResource(id = R.string.settings),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            SelectionActionsSheet(
                show = selectionManager.isInSelectionMode && selectionManager.selectionItems.isNotEmpty(),
                contentFocusRequester = focusState.requester,
                showRenameAction = selectionManager.isSingleVideoSelected,
                showInfoAction = selectionManager.isSingleVideoSelected,
                showHideAction = selectionManager.selectionItems.isNotEmpty(),
                onPlayAction = {
                    onAction(MediaPickerAction.PlaySelectedItems(selectionManager.selectionItems))
                    selectionManager.exitSelectionMode()
                },
                onAddToPlaylistAction = {
                    onAction(MediaPickerAction.ShowAddToPlaylist(selectionManager.selectionItems))
                },
                onRenameAction = {
                    val selectedVideo = selectionManager.selectionItems.firstOrNull() ?: return@SelectionActionsSheet
                    val video = (uiState.mediaDataState as? DataState.Success)?.value?.videos
                        ?.find { it.uriString == selectedVideo.id } ?: return@SelectionActionsSheet
                    showRenameActionFor = video
                },
                onInfoAction = {
                    val selectedVideo = selectionManager.selectionItems.firstOrNull() ?: return@SelectionActionsSheet
                    val video = (uiState.mediaDataState as? DataState.Success)?.value?.videos
                        ?.find { it.uriString == selectedVideo.id } ?: return@SelectionActionsSheet
                    onAction(MediaPickerAction.ShowMediaInfo(video))
                    selectionManager.exitSelectionMode()
                },
                onShareAction = {
                    onAction(MediaPickerAction.ShareSelectedItems(selectionManager.selectionItems))
                },
                onCopyAction = {
                    onAction(MediaPickerAction.CopySelectedItems(selectionManager.selectionItems))
                    selectionManager.exitSelectionMode()
                },
                onMoveAction = {
                    onAction(MediaPickerAction.MoveSelectedItems(selectionManager.selectionItems))
                    selectionManager.exitSelectionMode()
                },
                onHideAction = {
                    onAction(MediaPickerAction.RequestHideSelectedItems(selectionManager.selectionItems))
                    selectionManager.exitSelectionMode()
                },
                onDeleteAction = {
                    showDeleteVideosConfirmation = true
                },
            )
        },
        floatingActionButton = {
            if (uiState.folderName != null && !selectionManager.isInSelectionMode) {
                FloatingActionButton(onClick = onFabClick) {
                    Icon(imageVector = NextIcons.Play, contentDescription = null)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { scaffoldPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(scaffoldPadding.copy(bottom = 0.dp))
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (!permissionState.status.isGranted) {
                PermissionMissingView(
                    isGranted = permissionState.status.isGranted,
                    showRationale = permissionState.status.shouldShowRationale,
                    permission = permissionState.permission,
                    launchPermissionRequest = { permissionState.launchPermissionRequest() },
                ) {}
                return@Scaffold
            }
            when (uiState.mediaDataState) {
                is DataState.Error -> {
                }

                is DataState.Loading -> {
                    CenterCircularProgressBar()
                }

                is DataState.Success -> {
                    val successContent: @Composable () -> Unit = {
                        val updatedScaffoldPadding = scaffoldPadding.copy(
                            top = 0.dp,
                            start = 0.dp,
                            bottom = scaffoldPadding.calculateBottomPadding() + navigationBottomPadding,
                        )
                        val mediaHolder = uiState.mediaDataState.value
                        if (mediaHolder == null || mediaHolder.folders.isEmpty() && mediaHolder.videos.isEmpty()) {
                            NoVideosFound(contentPadding = updatedScaffoldPadding)
                        } else {
                            MediaView(
                                recentlyPlayedVideo = uiState.recentlyPlayedVideo,
                                recentlyPlayedFolder = uiState.recentlyPlayedFolder,
                                mediaHolder = mediaHolder,
                                preferences = uiState.preferences,
                                onFolderClick = { onAction(MediaPickerAction.OnFolderClick(it)) },
                                onVideoClick = { onAction(MediaPickerAction.OnPlayVideo(it)) },
                                selectionManager = selectionManager,
                                lazyGridState = lazyGridState,
                                focusState = focusState,
                                contentPadding = updatedScaffoldPadding,
                            )
                        }
                    }
                    if (isTv) {
                        successContent()
                    } else {
                        PullToRefreshBox(
                            isRefreshing = uiState.refreshing,
                            onRefresh = { onAction(MediaPickerAction.Refresh) },
                        ) { successContent() }
                    }
                }
            }
        }
    }

    BackHandler(enabled = selectionManager.isInSelectionMode) {
        selectionManager.exitSelectionMode()
    }

    if (showQuickSettingsDialog) {
        QuickSettingsDialog(
            applicationPreferences = uiState.preferences,
            onDismiss = { showQuickSettingsDialog = false },
            updatePreferences = { onAction(MediaPickerAction.UpdateMenu(it)) },
        )
    }

    showRenameActionFor?.let { video ->
        RenameDialog(
            name = video.displayName,
            onDismiss = { showRenameActionFor = null },
            onDone = {
                onAction(MediaPickerAction.RenameVideo(video.uriString.toUri(), it))
                selectionManager.exitSelectionMode()
                showRenameActionFor = null
            },
        )
    }

    uiState.mediaInfo?.let { mediaInfo ->
        MediaInfoDialog(
            mediaInfo = mediaInfo,
            onDismiss = { onAction(MediaPickerAction.DismissMediaInfo) },
        )
    }

    if (showDeleteVideosConfirmation) {
        DeleteConfirmationDialog(
            selectionItems = selectionManager.selectionItems,
            trashSupported = MediaOperationsService.supportsTrash(),
            onConfirm = { permanently ->
                onAction(MediaPickerAction.DeleteSelectedItems(selectionManager.selectionItems, permanently = permanently))
                selectionManager.exitSelectionMode()
                showDeleteVideosConfirmation = false
            },
            onCancel = { showDeleteVideosConfirmation = false },
        )
    }

    HideFlowDialogs(
        hideFlow = uiState.hideFlow,
        onConfirmHide = { onAction(MediaPickerAction.ConfirmHidePendingItems) },
        onSetPinAndHide = { onAction(MediaPickerAction.SetVaultPinAndHide(it)) },
        onBiometricSetupComplete = { onAction(MediaPickerAction.CompleteBiometricSetup(it)) },
        onDismiss = { onAction(MediaPickerAction.DismissHideFlow) },
    )

    (uiState.transferFlow as? TransferFlowState.Processing)?.let { transfer ->
        TransferProgressDialog(
            mode = transfer.mode,
            progress = transfer.progress,
            onCancel = { onAction(MediaPickerAction.CancelTransfer) },
        )
    }

    if (uiState.addToPlaylistState.isVisible) {
        PlaylistTargetDialog(
            playlists = uiState.playlists,
            state = uiState.addToPlaylistState,
            onDismissRequest = { onAction(MediaPickerAction.DismissAddToPlaylist) },
            onPlaylistSelected = {
                onAction(MediaPickerAction.AddSelectionToPlaylist(it))
                selectionManager.exitSelectionMode()
            },
            onCreatePlaylist = {
                onAction(MediaPickerAction.CreatePlaylistWithSelection(it))
                selectionManager.exitSelectionMode()
            },
        )
    }
}

@Composable
private fun TransferProgressDialog(
    mode: TransferMode,
    progress: TransferProgress,
    onCancel: () -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnClickOutside = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = when (mode) {
                        TransferMode.COPY -> stringResource(R.string.copying_videos_in_progress)
                        TransferMode.MOVE -> stringResource(R.string.moving_videos_in_progress)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                ProgressSection(
                    label = progress.currentName.orEmpty(),
                    fraction = progress.currentFraction,
                )

                if (progress.totalFiles > 1) {
                    ProgressSection(
                        label = stringResource(
                            R.string.transfer_file_progress,
                            progress.currentIndex + 1,
                            progress.totalFiles,
                        ),
                        fraction = progress.overallFraction,
                    )
                }

                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        }
    }
}

/**
 * A labelled progress bar. A null [fraction] renders an indeterminate bar with no percentage.
 */
@Composable
private fun ProgressSection(
    label: String,
    fraction: Float?,
) {
    val animatedFraction by animateFloatAsState(targetValue = fraction ?: 0f, label = "progress")

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (fraction != null) {
                Text(
                    text = "${(fraction * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val barModifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
        if (fraction != null) {
            LinearProgressIndicator(progress = { animatedFraction }, modifier = barModifier)
        } else {
            LinearProgressIndicator(modifier = barModifier)
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    modifier: Modifier = Modifier,
    selectionItems: Set<SelectionItem>,
    trashSupported: Boolean,
    onConfirm: (Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val selectedVideos = selectionItems.filterIsInstance<SelectionItem.Video>()
    val selectedFolders = selectionItems.filterIsInstance<SelectionItem.Folder>()
    var permanently by rememberSaveable { mutableStateOf(false) }

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
                onClick = { onConfirm(permanently || !trashSupported) },
                modifier = modifier,
            ) {
                Text(text = stringResource(if (permanently || !trashSupported) R.string.delete else R.string.move_to_trash))
            }
        },
        dismissButton = { CancelButton(onClick = onCancel) },
        modifier = modifier,
        content = {
            Text(
                text = if (permanently || !trashSupported) {
                    if ((selectedFolders.size + selectedVideos.size) == 1) {
                        stringResource(R.string.delete_item_info)
                    } else {
                        stringResource(R.string.delete_items_info)
                    }
                } else {
                    stringResource(R.string.move_to_trash_info)
                },
                style = MaterialTheme.typography.titleSmall,
            )
            if (trashSupported) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = permanently,
                        onCheckedChange = { permanently = it },
                    )
                    Text(text = stringResource(R.string.delete_permanently))
                }
            }
        },
    )
}

@Composable
private fun HideFlowDialogs(
    hideFlow: HideFlowState,
    onConfirmHide: () -> Unit,
    onSetPinAndHide: (String) -> Unit,
    onBiometricSetupComplete: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    when (hideFlow) {
        HideFlowState.Idle -> Unit

        HideFlowState.Processing -> {
            VaultProgressDialog(message = stringResource(R.string.hiding_videos_in_progress))
        }

        is HideFlowState.ConfirmHide -> {
            val count = hideFlow.items.size
            NextDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(
                        text = if (count == 1) {
                            stringResource(R.string.hide_one_video_confirmation)
                        } else {
                            stringResource(R.string.hide_videos_confirmation, count)
                        },
                    )
                },
                content = { Text(text = stringResource(R.string.hide_video_info)) },
                confirmButton = {
                    TextButton(onClick = onConfirmHide) {
                        Text(text = stringResource(R.string.hide))
                    }
                },
                dismissButton = { CancelButton(onClick = onDismiss) },
            )
        }

        is HideFlowState.SetupPin -> {
            SetupVaultPinDialog(
                onDismiss = onDismiss,
                onPinConfirmed = onSetPinAndHide,
            )
        }

        HideFlowState.BiometricSetup -> VaultBiometricSetupDialog(onComplete = onBiometricSetupComplete)

        HideFlowState.HowToFindInfo -> {
            NextDialog(
                onDismissRequest = onDismiss,
                title = { Text(text = stringResource(R.string.how_to_find_hidden_videos_title)) },
                content = { Text(text = stringResource(R.string.how_to_find_hidden_videos_description)) },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.got_it))
                    }
                },
            )
        }
    }
}

@Composable
private fun SetupVaultPinDialog(
    onDismiss: () -> Unit,
    onPinConfirmed: (String) -> Unit,
) {
    var firstPin: String? by rememberSaveable { mutableStateOf(null) }
    var currentPin by rememberSaveable { mutableStateOf("") }
    var errorCount by rememberSaveable { mutableStateOf(0) }

    val isConfirmStep = firstPin != null
    val showError = errorCount > 0

    val context = LocalContext.current
    val isTv = remember { context.isTelevision }

    val onDigit: (Char) -> Unit = { digit ->
        if (currentPin.length < VAULT_PIN_LENGTH) {
            currentPin += digit
            if (currentPin.length == VAULT_PIN_LENGTH) {
                val pinJustEntered = currentPin
                if (!isConfirmStep) {
                    firstPin = pinJustEntered
                    currentPin = ""
                    errorCount = 0
                } else if (pinJustEntered == firstPin) {
                    onPinConfirmed(pinJustEntered)
                } else {
                    errorCount++
                    currentPin = ""
                }
            }
        }
    }
    val onBackspace: () -> Unit = {
        if (currentPin.isNotEmpty()) currentPin = currentPin.dropLast(1)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = !isTv),
    ) {
        Surface(
            modifier = if (isTv) Modifier.fillMaxWidth(0.72f) else Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            if (isTv) {
                // Landscape TV: info on the left, keypad on the right so it fits a short screen.
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SetupPinHeader(
                        modifier = Modifier.weight(1f),
                        isConfirmStep = isConfirmStep,
                        filledCount = currentPin.length,
                        showError = showError,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PinKeypad(
                            modifier = Modifier.widthIn(max = 300.dp),
                            onDigit = onDigit,
                            onBackspace = onBackspace,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        CancelButton(onClick = onDismiss)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SetupPinHeader(
                        isConfirmStep = isConfirmStep,
                        filledCount = currentPin.length,
                        showError = showError,
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    PinKeypad(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        onDigit = onDigit,
                        onBackspace = onBackspace,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    CancelButton(onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun SetupPinHeader(
    isConfirmStep: Boolean,
    filledCount: Int,
    showError: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Lock icon — matches the vault PIN screen's icon box
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = NextIcons.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = if (isConfirmStep) {
                stringResource(R.string.confirm_vault_pin)
            } else {
                stringResource(R.string.set_vault_pin)
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isConfirmStep) {
                stringResource(R.string.confirm_vault_pin_description)
            } else {
                stringResource(R.string.set_vault_pin_description)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(32.dp))
        PinDotsIndicator(
            filledCount = filledCount,
            error = showError,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (showError) {
            Text(
                text = stringResource(R.string.pins_do_not_match),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PlaylistTargetDialog(
    playlists: List<PlaylistSummary>,
    state: AddToPlaylistState,
    onDismissRequest: () -> Unit,
    onPlaylistSelected: (Long) -> Unit,
    onCreatePlaylist: (String) -> Unit,
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }

    if (showCreateDialog) {
        var name by rememberSaveable { mutableStateOf("") }
        val trimmedName = name.trim()
        NextDialog(
            onDismissRequest = {
                if (!state.isSaving) showCreateDialog = false
            },
            title = {
                Text(stringResource(R.string.create_new_playlist))
            },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = {
                            Text(stringResource(R.string.playlist_name))
                        },
                        enabled = !state.isSaving,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.errorRes?.let { errorRes ->
                        Text(
                            text = stringResource(errorRes),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onCreatePlaylist(trimmedName) },
                    enabled = !state.isSaving && state.hasVideos && trimmedName.isNotEmpty(),
                    modifier = Modifier.tvFocusRing(),
                ) {
                    Text(stringResource(R.string.create))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!state.isSaving) showCreateDialog = false },
                    enabled = !state.isSaving,
                    modifier = Modifier.tvFocusRing(),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
        return
    }

    NextDialog(
        onDismissRequest = {
            if (!state.isSaving) onDismissRequest()
        },
        title = {
            Text(stringResource(R.string.choose_playlist))
        },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { showCreateDialog = true },
                    enabled = !state.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocusRing(),
                ) {
                    Icon(NextIcons.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.create_new_playlist),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = stringResource(R.string.existing_playlists),
                    style = MaterialTheme.typography.labelLarge,
                )
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    if (playlists.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.no_playlists),
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                    items(playlists, key = PlaylistSummary::id) { playlist ->
                        TextButton(
                            onClick = { onPlaylistSelected(playlist.id) },
                            enabled = !state.isSaving && state.hasVideos,
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusRing(),
                        ) {
                            Icon(
                                imageVector = NextIcons.Playlist,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.size(8.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.Start,
                            ) {
                                Text(
                                    text = playlist.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.playlist_video_count,
                                        playlist.itemCount,
                                        playlist.itemCount,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                state.errorRes?.let { errorRes ->
                    Text(
                        text = stringResource(errorRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (state.isSaving) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                enabled = !state.isSaving,
                modifier = Modifier.tvFocusRing(),
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SelectionActionsSheet(
    modifier: Modifier = Modifier,
    show: Boolean,
    contentFocusRequester: FocusRequester,
    showRenameAction: Boolean,
    showInfoAction: Boolean,
    showHideAction: Boolean,
    onPlayAction: () -> Unit,
    onAddToPlaylistAction: () -> Unit,
    onRenameAction: () -> Unit,
    onShareAction: () -> Unit,
    onCopyAction: () -> Unit,
    onMoveAction: () -> Unit,
    onInfoAction: () -> Unit,
    onHideAction: () -> Unit,
    onDeleteAction: () -> Unit,
) {
    val context = LocalContext.current
    val isTv = remember { context.isTelevision }
    val firstActionFocusRequester = remember { FocusRequester() }
    AnimatedVisibility(
        modifier = modifier.windowInsetsPadding(
            WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
        ),
        visible = show,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
        val shape = MaterialTheme.shapes.largeIncreased.copy(
            bottomStart = ZeroCornerSize,
            bottomEnd = ZeroCornerSize,
        )
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
                    .thenIf(isTv) {
                        focusRestorer(fallback = firstActionFocusRequester).focusGroup()
                            .focusProperties { up = contentFocusRequester }
                    }
                    .navigationBarsPadding()
                    .padding(
                        horizontal = 8.dp,
                        vertical = 12.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SelectionAction(
                    modifier = Modifier.thenIf(isTv) { focusRequester(firstActionFocusRequester) },
                    isTv = isTv,
                    imageVector = NextIcons.Play,
                    title = stringResource(R.string.play),
                    onClick = onPlayAction,
                )
                SelectionAction(
                    isTv = isTv,
                    imageVector = NextIcons.PlaylistAdd,
                    title = stringResource(R.string.add_to_playlist),
                    onClick = onAddToPlaylistAction,
                )
                if (showRenameAction) {
                    SelectionAction(
                        isTv = isTv,
                        imageVector = NextIcons.Edit,
                        title = stringResource(R.string.rename),
                        onClick = onRenameAction,
                    )
                }
                SelectionAction(
                    isTv = isTv,
                    imageVector = NextIcons.Share,
                    title = stringResource(R.string.share),
                    onClick = onShareAction,
                )
                SelectionAction(
                    isTv = isTv,
                    imageVector = NextIcons.Copy,
                    title = stringResource(R.string.copy),
                    onClick = onCopyAction,
                )
                SelectionAction(
                    isTv = isTv,
                    imageVector = NextIcons.Move,
                    title = stringResource(R.string.move),
                    onClick = onMoveAction,
                )
                if (showInfoAction) {
                    SelectionAction(
                        isTv = isTv,
                        imageVector = NextIcons.Info,
                        title = stringResource(id = R.string.info),
                        onClick = onInfoAction,
                    )
                }
                if (showHideAction) {
                    SelectionAction(
                        isTv = isTv,
                        imageVector = NextIcons.HideSource,
                        title = stringResource(id = R.string.hide),
                        onClick = onHideAction,
                    )
                }
                SelectionAction(
                    isTv = isTv,
                    imageVector = NextIcons.Delete,
                    title = stringResource(id = R.string.delete),
                    onClick = onDeleteAction,
                )
            }
        }
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
                    value = MediaHolder(
                        folders = listOf(
                            Folder(name = "Folder 1", path = "/root/folder1", dateModified = System.currentTimeMillis()),
                            Folder(name = "Folder 2", path = "/root/folder2", dateModified = System.currentTimeMillis()),
                        ),
                        videos = videos,
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
