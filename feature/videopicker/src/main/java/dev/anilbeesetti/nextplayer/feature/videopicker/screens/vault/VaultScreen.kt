package dev.anilbeesetti.nextplayer.feature.videopicker.screens.vault

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.model.MediaLayoutMode
import dev.anilbeesetti.nextplayer.core.model.Sort
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.CancelButton
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.requestFocusUntilLanded
import dev.anilbeesetti.nextplayer.core.ui.components.restorableFocusItem
import dev.anilbeesetti.nextplayer.core.ui.components.thenIf
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.extensions.copy
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.MediaInfoDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.vault.PinDotsIndicator
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.vault.PinKeypad
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.vault.VaultProgressDialog
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
    val context = LocalContext.current

    ObserveAsEvents(flow = viewModel.events) { event ->
        when (event) {
            is VaultEvent.PlayVideo -> onPlayVideo(event.uri)
            is VaultEvent.PlayVideos -> onPlayVideos(event.uris)

            is VaultEvent.VideosRelocated -> {
                val message = context.resources.getQuantityString(
                    R.plurals.videos_relocated_to_movies,
                    event.count,
                    event.count,
                )
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
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
        VaultStage.LOCKED -> VaultPinScreen(
            title = stringResource(R.string.enter_vault_pin),
            description = stringResource(R.string.enter_vault_pin_description),
            pinErrorCount = uiState.pinErrorCount,
            errorMessage = stringResource(R.string.incorrect_pin),
            onSubmit = { onAction(VaultAction.SubmitUnlockPin(it)) },
            onNavigateUp = onNavigateUp,
        )

        VaultStage.SET_PIN -> VaultPinScreen(
            title = stringResource(R.string.set_vault_pin),
            description = stringResource(R.string.set_vault_pin_description),
            pinErrorCount = uiState.setPinGeneration,
            onSubmit = { onAction(VaultAction.SubmitNewPin(it)) },
            onNavigateUp = onNavigateUp,
        )

        VaultStage.CONFIRM_PIN -> VaultPinScreen(
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
        VaultProgressDialog(message = stringResource(R.string.unhiding_videos_in_progress))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultPinScreen(
    title: String,
    description: String,
    pinErrorCount: Int,
    onSubmit: (String) -> Unit,
    onNavigateUp: () -> Unit,
    errorMessage: String = "",
) {
    Scaffold(
        topBar = {
            NextTopAppBar(
                title = "",
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier.tvFocusRing(),
                    ) {
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
            errorMessage = errorMessage,
            onSubmit = onSubmit,
        )
    }
}

@Composable
private fun PinEntryContent(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    description: String,
    pinErrorCount: Int,
    errorMessage: String,
    onSubmit: (String) -> Unit,
) {
    // pin resets on every wrong attempt because pinErrorCount increments each time
    var pin by rememberSaveable(pinErrorCount) { mutableStateOf("") }
    // showError is true whenever there has been at least one error, resets when pinErrorCount resets to 0
    var showError by rememberSaveable(pinErrorCount) { mutableStateOf(pinErrorCount > 0) }

    val context = LocalContext.current
    val isTv = remember { context.isTelevision }

    val onDigit: (Char) -> Unit = { digit ->
        if (pin.length < VAULT_PIN_LENGTH) {
            showError = false
            pin += digit
            if (pin.length == VAULT_PIN_LENGTH) {
                onSubmit(pin)
            }
        }
    }
    val onBackspace: () -> Unit = {
        if (pin.isNotEmpty()) pin = pin.dropLast(1)
    }

    if (isTv) {
        // Landscape two-pane layout: the info sits on the left, the keypad (kept at a comfortable
        // width so the keys don't stretch across the screen) on the right.
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PinEntryHeader(
                modifier = Modifier.weight(1f),
                icon = icon,
                title = title,
                description = description,
                filledCount = pin.length,
                showError = showError,
                errorMessage = errorMessage,
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                PinKeypad(
                    modifier = Modifier.widthIn(max = 360.dp),
                    onDigit = onDigit,
                    onBackspace = onBackspace,
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.size(16.dp))
            PinEntryHeader(
                icon = icon,
                title = title,
                description = description,
                filledCount = pin.length,
                showError = showError,
                errorMessage = errorMessage,
            )
            Spacer(modifier = Modifier.size(32.dp))
            PinKeypad(
                modifier = Modifier.padding(horizontal = 24.dp),
                onDigit = onDigit,
                onBackspace = onBackspace,
            )
            Spacer(modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun PinEntryHeader(
    icon: ImageVector,
    title: String,
    description: String,
    filledCount: Int,
    showError: Boolean,
    errorMessage: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
        PinDotsIndicator(filledCount = filledCount, error = showError)
        Spacer(modifier = Modifier.size(8.dp))
        if (showError && errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
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
    val context = LocalContext.current
    val isTv = remember { context.isTelevision }
    val firstActionFocusRequester = remember { FocusRequester() }
    val firstItemRequester = remember { FocusRequester() }
    val restoreRequester = remember { FocusRequester() }
    var restoredFocusKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selectionManager = rememberSelectionManager()
    var showSortMenu by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    var showUnhideConfirmation by rememberSaveable { mutableStateOf(false) }

    val selectedCount = selectionManager.selectionItems.size
    val totalCount = uiState.hiddenVideos.size

    var hasRequestedInitialFocus by remember { mutableStateOf(false) }
    if (isTv) {
        LaunchedEffect(uiState.hiddenVideos.size) {
            if (hasRequestedInitialFocus || uiState.hiddenVideos.isEmpty()) return@LaunchedEffect
            val hasRestore = restoredFocusKey != null && uiState.hiddenVideos.any { it.uriString == restoredFocusKey }
            // Prefer restoring the previously focused item; fall back to the first item.
            val targets = if (hasRestore) listOf(restoreRequester, firstItemRequester) else listOf(firstItemRequester)
            hasRequestedInitialFocus = targets.any { it.requestFocusUntilLanded() }
        }
    }

    BackHandler(enabled = selectionManager.isInSelectionMode) {
        selectionManager.exitSelectionMode()
    }

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = {
                    val titleText = stringResource(R.string.hidden_videos).takeIf { !selectionManager.isInSelectionMode } ?: ""
                    Text(
                        text = titleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
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
                                text = stringResource(R.string.m_n_selected, selectedCount, totalCount),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    } else {
                        FilledTonalIconButton(
                            onClick = onNavigateUp,
                            modifier = Modifier.tvFocusRing(),
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
                                if (selectedCount != totalCount) {
                                    uiState.hiddenVideos.forEach { selectionManager.selectVideo(it) }
                                } else {
                                    selectionManager.clearSelection()
                                }
                            },
                            modifier = Modifier.tvFocusRing(),
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
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.tvFocusRing(),
                        ) {
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
                firstActionFocusRequester = firstActionFocusRequester,
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .padding(start = padding.calculateStartPadding(LocalLayoutDirection.current))
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MaterialTheme.colorScheme.background),
        ) {
            val updatedPadding = padding.copy(top = 0.dp, start = 0.dp)
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(updatedPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.hiddenVideos.isEmpty() -> {
                    VaultEmptyState(contentPadding = updatedPadding)
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
                            top = updatedPadding.calculateTopPadding() + 8.dp,
                            bottom = updatedPadding.calculateBottomPadding() + 8.dp,
                        ),
                    ) {
                        itemsIndexed(
                            items = uiState.hiddenVideos,
                            key = { _, video -> video.uriString },
                        ) { index, video ->
                            val selected = selectionManager.isVideoSelected(video)
                            VideoItem(
                                modifier = Modifier
                                    .padding(2.dp)
                                    .thenIf(isTv && index == 0) { focusRequester(firstItemRequester) }
                                    // Down from the last item reaches the selection action bar.
                                    .thenIf(
                                        isTv && selectionManager.isInSelectionMode &&
                                            index == uiState.hiddenVideos.lastIndex,
                                    ) {
                                        focusProperties { down = firstActionFocusRequester }
                                    }
                                    .restorableFocusItem(
                                        isTv = isTv,
                                        key = video.uriString,
                                        restoredKey = restoredFocusKey,
                                        restoreRequester = restoreRequester,
                                        onFocused = { restoredFocusKey = it },
                                    ),
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
    modifier: Modifier = Modifier,
    show: Boolean,
    firstActionFocusRequester: FocusRequester,
    onPlayAction: () -> Unit,
    onInfoAction: () -> Unit,
    showInfoAction: Boolean,
    onUnhideAction: () -> Unit,
    onDeleteAction: () -> Unit,
) {
    val context = LocalContext.current
    val isTv = remember { context.isTelevision }

    AnimatedVisibility(
        modifier = modifier.padding(
            start = WindowInsets.displayCutout.asPaddingValues()
                .calculateStartPadding(LocalLayoutDirection.current),
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
                    .navigationBarsPadding()
                    .padding(
                        horizontal = 8.dp,
                        vertical = 12.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                VaultSelectionAction(
                    modifier = Modifier.focusRequester(firstActionFocusRequester),
                    isTv = isTv,
                    imageVector = NextIcons.Play,
                    title = stringResource(R.string.play),
                    onClick = onPlayAction,
                )
                if (showInfoAction) {
                    VaultSelectionAction(
                        isTv = isTv,
                        imageVector = NextIcons.Info,
                        title = stringResource(R.string.info),
                        onClick = onInfoAction,
                    )
                }
                VaultSelectionAction(
                    isTv = isTv,
                    imageVector = NextIcons.Lock,
                    title = stringResource(R.string.unhide),
                    onClick = onUnhideAction,
                )
                VaultSelectionAction(
                    isTv = isTv,
                    imageVector = NextIcons.Delete,
                    title = stringResource(R.string.delete),
                    onClick = onDeleteAction,
                )
            }
        }
    }
}

@Composable
private fun VaultSelectionAction(
    imageVector: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isTv: Boolean = false,
) {
    Column(
        modifier = modifier
            .defaultMinSize(
                minWidth = 75.dp,
                minHeight = 64.dp,
            )
            .tvFocusRing(isTv, shape = RoundedCornerShape(12.dp))
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

@PreviewLightDark
@Composable
private fun VaultPinScreenPreview() {
    NextPlayerTheme {
        VaultPinScreen(
            title = stringResource(R.string.enter_vault_pin),
            description = stringResource(R.string.enter_vault_pin_description),
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
