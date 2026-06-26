package dev.anilbeesetti.nextplayer.feature.videopicker.screens.vault

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.model.MediaLayoutMode
import dev.anilbeesetti.nextplayer.core.model.Sort
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.CancelButton
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.MediaInfoDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.vault.PinDotsIndicator
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.vault.PinKeypad
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.VideoItem
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.mediapicker.ObserveAsEvents
import dev.anilbeesetti.nextplayer.feature.videopicker.state.SelectionItem
import dev.anilbeesetti.nextplayer.feature.videopicker.state.rememberSelectionManager

@Composable
fun VaultRoute(
    viewModel: VaultViewModel = hiltViewModel(),
    onPlayVideo: (uri: Uri) -> Unit,
    onPlayVideos: (uris: List<Uri>) -> Unit,
    onNavigateUp: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveAsEvents(flow = viewModel.events) { event ->
        when (event) {
            is VaultEvent.PlayVideos -> {
                if (event.uris.size == 1) onPlayVideo(event.uris.first()) else onPlayVideos(event.uris)
            }
        }
    }

    VaultScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateUp = onNavigateUp,
    )
}

@Composable
internal fun VaultScreen(
    uiState: VaultUiState,
    onAction: (VaultAction) -> Unit,
    onNavigateUp: () -> Unit,
) {
    when (uiState.stage) {
        VaultStage.LOCKED -> VaultUnlockScreen(
            pinErrorCount = uiState.pinErrorCount,
            onSubmit = { onAction(VaultAction.SubmitUnlockPin(it)) },
            onNavigateUp = onNavigateUp,
        )

        VaultStage.SET_PIN -> VaultPinEntryScreen(
            title = stringResource(R.string.set_vault_pin),
            description = stringResource(R.string.set_vault_pin_description),
            pinErrorCount = uiState.setPinGeneration,
            onSubmit = { onAction(VaultAction.SubmitNewPin(it)) },
            onNavigateUp = onNavigateUp,
        )

        VaultStage.CONFIRM_PIN -> VaultPinEntryScreen(
            title = stringResource(R.string.confirm_vault_pin),
            description = stringResource(R.string.confirm_vault_pin_description),
            pinErrorCount = uiState.pinErrorCount,
            errorMessage = stringResource(R.string.pins_do_not_match),
            onSubmit = { onAction(VaultAction.SubmitPinConfirmation(it)) },
            onNavigateUp = onNavigateUp,
        )

        VaultStage.HOW_TO_FIND_INFO -> {
            HowToFindHiddenVideosDialog(
                onDismiss = { onAction(VaultAction.DismissHowToFindInfo) },
            )
        }

        VaultStage.UNLOCKED -> VaultGalleryScreen(
            uiState = uiState,
            onAction = onAction,
            onNavigateUp = onNavigateUp,
        )
    }

    if (uiState.isUnhiding) {
        Dialog(onDismissRequest = {}) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f),
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp,
                        )
                        Text(
                            text = stringResource(R.string.unhiding_videos_in_progress),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultUnlockScreen(
    pinErrorCount: Int,
    onSubmit: (String) -> Unit,
    onNavigateUp: () -> Unit,
) {
    Scaffold(
        topBar = {
            NextTopAppBar(
                title = "",
                navigationIcon = {
                    FilledTonalIconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { padding ->
        PinEntryContent(
            modifier = Modifier.padding(padding),
            icon = NextIcons.Lock,
            title = stringResource(R.string.enter_vault_pin),
            description = stringResource(R.string.enter_vault_pin_description),
            pinErrorCount = pinErrorCount,
            errorMessage = stringResource(R.string.incorrect_pin),
            onSubmit = onSubmit,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultPinEntryScreen(
    title: String,
    description: String,
    pinErrorCount: Int,
    onSubmit: (String) -> Unit,
    onNavigateUp: () -> Unit,
    errorMessage: String? = null,
) {
    Scaffold(
        topBar = {
            NextTopAppBar(
                title = "",
                navigationIcon = {
                    FilledTonalIconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { padding ->
        PinEntryContent(
            modifier = Modifier.padding(padding),
            icon = NextIcons.Lock,
            title = title,
            description = description,
            pinErrorCount = pinErrorCount,
            errorMessage = errorMessage ?: "",
            onSubmit = onSubmit,
        )
    }
}

@Composable
private fun PinEntryContent(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    pinErrorCount: Int,
    errorMessage: String,
    onSubmit: (String) -> Unit,
    footer: @Composable (() -> Unit)? = null,
) {
    // pin resets on every wrong attempt because pinErrorCount increments each time
    var pin by rememberSaveable(pinErrorCount) { mutableStateOf("") }
    // showError is true whenever there has been at least one error, resets when pinErrorCount resets to 0
    var showError by rememberSaveable(pinErrorCount) { mutableStateOf(pinErrorCount > 0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.size(16.dp))
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(modifier = Modifier.size(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.size(32.dp))
        PinDotsIndicator(filledCount = pin.length, error = showError)
        Spacer(modifier = Modifier.size(8.dp))
        if (showError && errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.size(32.dp))
        PinKeypad(
            modifier = Modifier.padding(horizontal = 24.dp),
            onDigit = { digit ->
                if (pin.length < VAULT_PIN_LENGTH) {
                    showError = false
                    pin += digit
                    if (pin.length == VAULT_PIN_LENGTH) {
                        onSubmit(pin)
                    }
                }
            },
            onBackspace = {
                if (pin.isNotEmpty()) pin = pin.dropLast(1)
            },
        )
        Spacer(modifier = Modifier.size(16.dp))
        footer?.invoke()
        Spacer(modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun HowToFindHiddenVideosDialog(
    onDismiss: () -> Unit,
) {
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VaultGalleryScreen(
    uiState: VaultUiState,
    onAction: (VaultAction) -> Unit,
    onNavigateUp: () -> Unit,
) {
    val selectionManager = rememberSelectionManager()
    var showSortMenu by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    var showUnhideConfirmation by rememberSaveable { mutableStateOf(false) }

    val selectedCount = selectionManager.selectionItems.size
    val totalCount = uiState.hiddenVideos.size

    BackHandler(enabled = selectionManager.isInSelectionMode) {
        selectionManager.exitSelectionMode()
    }

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = if (selectionManager.isInSelectionMode) "" else stringResource(R.string.hidden_videos),
                fontWeight = FontWeight.Bold,
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
                                text = stringResource(R.string.m_n_selected, selectedCount, totalCount),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    } else {
                        FilledTonalIconButton(onClick = onNavigateUp) {
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
                                if (selectedCount != totalCount) {
                                    uiState.hiddenVideos.forEach { selectionManager.selectVideo(it) }
                                } else {
                                    selectionManager.clearSelection()
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (selectedCount != totalCount) NextIcons.SelectAll else NextIcons.DeselectAll,
                                contentDescription = if (selectedCount != totalCount) {
                                    stringResource(R.string.select_all)
                                } else {
                                    stringResource(R.string.deselect_all)
                                },
                            )
                        }
                    } else {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                imageVector = NextIcons.Sensitivity,
                                contentDescription = stringResource(R.string.sort_by),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            VaultSelectionActionsSheet(
                show = selectionManager.isInSelectionMode && selectionManager.selectionItems.isNotEmpty(),
                onPlayAction = {
                    onAction(VaultAction.PlaySelected(selectionManager.selectionItems))
                    selectionManager.exitSelectionMode()
                },
                onInfoAction = {
                    val selected = selectionManager.selectionItems.firstOrNull()
                    val video = uiState.hiddenVideos.find { it.uriString == (selected as? SelectionItem.Video)?.uriString }
                    if (video != null) onAction(VaultAction.ShowMediaInfo(video))
                    selectionManager.exitSelectionMode()
                },
                showInfoAction = selectionManager.selectionItems.size == 1,
                onUnhideAction = { showUnhideConfirmation = true },
                onDeleteAction = { showDeleteConfirmation = true },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.hiddenVideos.isEmpty() -> {
                VaultEmptyState(contentPadding = padding)
            }

            else -> {
                LazyVerticalGrid(
                    modifier = Modifier.fillMaxSize(),
                    columns = if (uiState.preferences.mediaLayoutMode == MediaLayoutMode.GRID) {
                        GridCells.Adaptive(minSize = 130.dp)
                    } else {
                        GridCells.Fixed(1)
                    },
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 8.dp,
                    ),
                ) {
                    items(
                        items = uiState.hiddenVideos,
                        key = { it.uriString },
                    ) { video ->
                        val selected = selectionManager.isVideoSelected(video)
                        VideoItem(
                            modifier = Modifier.padding(2.dp),
                            video = video,
                            isRecentlyPlayedVideo = false,
                            preferences = uiState.preferences,
                            selected = selected,
                            onClick = {
                                if (selectionManager.isInSelectionMode) {
                                    selectionManager.toggleVideoSelection(video)
                                } else {
                                    onAction(VaultAction.PlayVideo(video))
                                }
                            },
                            onLongClick = {
                                selectionManager.toggleVideoSelection(video)
                            },
                        )
                    }
                }
            }
        }
    }

    if (showSortMenu) {
        VaultSortDialog(
            sort = uiState.sort,
            onDismiss = { showSortMenu = false },
            onSortSelected = {
                onAction(VaultAction.UpdateSort(it))
                showSortMenu = false
            },
        )
    }

    if (showDeleteConfirmation) {
        NextDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = {
                Text(
                    text = if (selectedCount == 1) {
                        stringResource(R.string.delete_one_video_from_vault)
                    } else {
                        stringResource(R.string.delete_videos_from_vault, selectedCount)
                    },
                )
            },
            content = { Text(text = stringResource(R.string.delete_from_vault_info)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAction(VaultAction.DeleteSelected(selectionManager.selectionItems))
                        selectionManager.exitSelectionMode()
                        showDeleteConfirmation = false
                    },
                ) {
                    Text(text = stringResource(R.string.delete))
                }
            },
            dismissButton = { CancelButton(onClick = { showDeleteConfirmation = false }) },
        )
    }

    if (showUnhideConfirmation) {
        NextDialog(
            onDismissRequest = { showUnhideConfirmation = false },
            title = { Text(text = stringResource(R.string.unhide)) },
            content = {
                Text(
                    text = if (selectedCount == 1) {
                        stringResource(R.string.unhide_one_video_confirmation)
                    } else {
                        stringResource(R.string.unhide_videos_confirmation, selectedCount)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAction(VaultAction.UnhideSelected(selectionManager.selectionItems))
                        selectionManager.exitSelectionMode()
                        showUnhideConfirmation = false
                    },
                ) {
                    Text(text = stringResource(R.string.unhide))
                }
            },
            dismissButton = { CancelButton(onClick = { showUnhideConfirmation = false }) },
        )
    }

    uiState.mediaInfo?.let { mediaInfo ->
        MediaInfoDialog(
            mediaInfo = mediaInfo,
            onDismiss = { onAction(VaultAction.DismissMediaInfo) },
        )
    }
}

@Composable
private fun VaultEmptyState(contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 24.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = NextIcons.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = stringResource(R.string.vault_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.vault_empty_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun VaultSortDialog(
    sort: Sort,
    onDismiss: () -> Unit,
    onSortSelected: (Sort) -> Unit,
) {
    val options = listOf(
        Sort.By.TITLE to stringResource(R.string.name),
        Sort.By.DATE to stringResource(R.string.date_hidden),
        Sort.By.SIZE to stringResource(R.string.size),
        Sort.By.LENGTH to stringResource(R.string.duration),
    )
    NextDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.sort_by)) },
        content = {
            Column {
                options.forEach { (by, label) ->
                    val selected = sort.by == by
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                val newOrder = if (selected) {
                                    if (sort.order == Sort.Order.ASCENDING) Sort.Order.DESCENDING else Sort.Order.ASCENDING
                                } else {
                                    sort.order
                                }
                                onSortSelected(Sort(by = by, order = newOrder))
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (selected) NextIcons.CheckBox else NextIcons.CheckBoxOutline,
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Text(text = label)
                        if (selected) {
                            Spacer(modifier = Modifier.size(8.dp))
                            Icon(
                                imageVector = if (sort.order == Sort.Order.ASCENDING) NextIcons.ArrowUpward else NextIcons.ArrowDownward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.done))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VaultSelectionActionsSheet(
    show: Boolean,
    onPlayAction: () -> Unit,
    onInfoAction: () -> Unit,
    showInfoAction: Boolean,
    onUnhideAction: () -> Unit,
    onDeleteAction: () -> Unit,
) {
    if (!show) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VaultActionItem(icon = NextIcons.Play, label = stringResource(R.string.play), onClick = onPlayAction)
            if (showInfoAction) {
                VaultActionItem(icon = NextIcons.Info, label = stringResource(R.string.info), onClick = onInfoAction)
            }
            VaultActionItem(icon = NextIcons.Lock, label = stringResource(R.string.unhide), onClick = onUnhideAction)
            VaultActionItem(icon = NextIcons.Delete, label = stringResource(R.string.delete), onClick = onDeleteAction)
        }
    }
}

@Composable
private fun VaultActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@PreviewLightDark
@Composable
private fun VaultUnlockScreenPreview() {
    NextPlayerTheme {
        VaultUnlockScreen(
            pinErrorCount = 0,
            onSubmit = {},
            onNavigateUp = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun VaultEmptyStatePreview() {
    NextPlayerTheme {
        Surface {
            VaultEmptyState(contentPadding = PaddingValues())
        }
    }
}
