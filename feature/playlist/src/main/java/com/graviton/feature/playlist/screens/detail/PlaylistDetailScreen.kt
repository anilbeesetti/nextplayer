package com.graviton.feature.playlist.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.graviton.core.common.extensions.isTelevision
import com.graviton.core.model.Playlist
import com.graviton.core.model.PlaylistItem
import com.graviton.core.ui.R
import com.graviton.core.ui.base.DataState
import com.graviton.core.ui.components.NextDialog
import com.graviton.core.ui.components.NextSegmentedListItem
import com.graviton.core.ui.components.NextTopAppBar
import com.graviton.core.ui.components.rememberTvListFocusRequester
import com.graviton.core.ui.components.tvFocusRing
import com.graviton.core.ui.components.tvListFocus
import com.graviton.core.ui.designsystem.NextIcons
import sh.calvin.reorderable.DragGestureDetector
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PlaylistDetailScreenRoute(
    viewModel: PlaylistDetailViewModel,
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    PlaylistDetailScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
    )
}

@Composable
internal fun PlaylistDetailScreen(
    uiState: PlaylistDetailUiState,
    onAction: (PlaylistDetailUiAction) -> Unit = {},
) {
    val isTv = LocalContext.current.isTelevision
    val playlist = (uiState.playlistDataState as? DataState.Success)?.value
    val videoUris = playlist?.items.orEmpty().map { it.video.uriString.toUri() }
    val playbackStartUri = playlist?.lastPlayedVideo
        ?.uriString
        ?.toUri()
        ?: videoUris.firstOrNull()
    val isReordering = uiState.isReordering && !isTv
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val exitSearch: () -> Unit = {
        onAction(PlaylistDetailUiAction.OnCloseSearchClick)
        keyboardController?.hide()
    }

    LaunchedEffect(uiState.isSearching) {
        if (uiState.isSearching) searchFocusRequester.requestFocus()
    }
    LaunchedEffect(isTv, uiState.isReordering) {
        if (isTv && uiState.isReordering) {
            onAction(PlaylistDetailUiAction.OnFinishReorderingClick)
        }
    }
    BackHandler(enabled = uiState.isSearching, onBack = exitSearch)

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = {
                    if (uiState.isSearching) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = {
                                onAction(PlaylistDetailUiAction.OnSearchQueryChange(it))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester),
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.search_playlist),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = exitSearch) {
                                    Icon(
                                        imageVector = NextIcons.Close,
                                        contentDescription = stringResource(R.string.close_search),
                                    )
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { keyboardController?.hide() },
                            ),
                            shape = CircleShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                errorBorderColor = Color.Transparent,
                                disabledBorderColor = Color.Transparent,
                            ),
                        )
                    } else {
                        Text(
                            text = playlist?.name.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = {
                            onAction(PlaylistDetailUiAction.OnNavigateUpClick)
                        },
                        modifier = Modifier.tvFocusRing(),
                    ) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
                actions = {
                    if (!uiState.isSearching) {
                        if (isReordering) {
                            IconButton(
                                onClick = {
                                    onAction(PlaylistDetailUiAction.OnFinishReorderingClick)
                                },
                                enabled = !uiState.updateActionState.isRunning,
                                modifier = Modifier.tvFocusRing(),
                            ) {
                                Icon(
                                    imageVector = NextIcons.Check,
                                    contentDescription = stringResource(R.string.finish_reordering),
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    onAction(PlaylistDetailUiAction.OnSearchClick)
                                },
                                enabled = playlist != null,
                                modifier = Modifier.tvFocusRing(),
                            ) {
                                Icon(
                                    imageVector = NextIcons.Search,
                                    contentDescription = stringResource(R.string.search),
                                )
                            }
                            if (!isTv) {
                                IconButton(
                                    onClick = {
                                        onAction(PlaylistDetailUiAction.OnReorderClick)
                                    },
                                    enabled = videoUris.size > 1 &&
                                        !uiState.updateActionState.isRunning,
                                    modifier = Modifier.tvFocusRing(),
                                ) {
                                    Icon(
                                        imageVector = NextIcons.Reorder,
                                        contentDescription = stringResource(R.string.reorder_playlist),
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!uiState.isSearching && !isReordering && playbackStartUri != null) {
                FloatingActionButton(
                    onClick = {
                        onAction(
                            PlaylistDetailUiAction.OnPlayVideos(
                                uris = videoUris,
                                startUri = playbackStartUri,
                            ),
                        )
                    },
                    modifier = Modifier.tvFocusRing(shape = MaterialTheme.shapes.large),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(
                        imageVector = NextIcons.Play,
                        contentDescription = stringResource(R.string.play),
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { padding ->
        val containerModifier = Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding())
            .padding(start = padding.calculateStartPadding(LocalLayoutDirection.current) + 2.dp)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(MaterialTheme.colorScheme.background)

        when (uiState.playlistDataState) {
            DataState.Loading ->
                Box(containerModifier, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            is DataState.Error -> PlaylistUnavailable(containerModifier)

            is DataState.Success -> {
                if (playlist == null) {
                    PlaylistUnavailable(containerModifier)
                } else {
                    PlaylistDetailContent(
                        playlist = playlist,
                        isTv = isTv,
                        isReordering = isReordering,
                        searchQuery = uiState.searchQuery,
                        showPlayFab = !uiState.isSearching &&
                            !isReordering &&
                            playbackStartUri != null,
                        actionsEnabled = !uiState.updateActionState.isRunning,
                        scaffoldPadding = padding,
                        onAction = onAction,
                        modifier = containerModifier,
                    )
                }
            }
        }
    }

    uiState.showRemoveDialogFor?.let { item ->
        RemoveVideoDialog(
            item = item,
            onConfirm = {
                onAction(PlaylistDetailUiAction.RemoveVideo(item.video.uriString))
            },
            onDismissRequest = {
                onAction(PlaylistDetailUiAction.DismissRemoveDialog)
            },
        )
    }
}

@Composable
private fun PlaylistDetailContent(
    playlist: Playlist,
    isTv: Boolean,
    isReordering: Boolean,
    searchQuery: String,
    showPlayFab: Boolean,
    actionsEnabled: Boolean,
    scaffoldPadding: PaddingValues,
    onAction: (PlaylistDetailUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var displayedItems by remember { mutableStateOf(playlist.items) }
    var isDragging by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current
    val visibleItems = remember(displayedItems, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            displayedItems
        } else {
            displayedItems.filter { item ->
                item.video.displayName.contains(query, ignoreCase = true) ||
                    item.video.parentPath.contains(query, ignoreCase = true)
            }
        }
    }

    LaunchedEffect(playlist.items) {
        if (!isDragging) displayedItems = playlist.items
    }

    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        displayedItems = displayedItems.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    Box(modifier = modifier) {
        if (displayedItems.isEmpty()) {
            PlaylistEmptyState(Modifier.fillMaxSize())
        } else if (visibleItems.isEmpty()) {
            PlaylistSearchEmptyState(Modifier.fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .tvListFocus(rememberTvListFocusRequester()),
                state = listState,
                contentPadding = PaddingValues(
                    start = 8.dp,
                    top = 8.dp,
                    end = 8.dp,
                    bottom = scaffoldPadding.calculateBottomPadding() +
                        if (showPlayFab) 96.dp else 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(
                    items = visibleItems,
                    key = { _, item -> item.video.uriString },
                ) { index, item ->
                    val onPlay = {
                        val uris = displayedItems.map { it.video.uriString.toUri() }
                        onAction(
                            PlaylistDetailUiAction.OnPlayVideos(
                                uris = uris,
                                startUri = item.video.uriString.toUri(),
                            ),
                        )
                    }
                    if (isReordering && !isTv) {
                        ReorderableItem(
                            state = reorderState,
                            key = item.video.uriString,
                        ) {
                            PlaylistVideoRow(
                                item = item,
                                isFirstItem = index == 0,
                                isLastItem = index == visibleItems.lastIndex,
                                isTv = false,
                                isReordering = true,
                                actionsEnabled = actionsEnabled,
                                modifier = Modifier.draggableHandle(
                                    enabled = actionsEnabled,
                                    dragGestureDetector = DragGestureDetector.LongPress,
                                    onDragStarted = {
                                        isDragging = true
                                        hapticFeedback.performHapticFeedback(
                                            HapticFeedbackType.GestureThresholdActivate,
                                        )
                                    },
                                    onDragStopped = {
                                        isDragging = false
                                        onAction(
                                            PlaylistDetailUiAction.ReplaceOrder(
                                                displayedItems.map { it.video.uriString },
                                            ),
                                        )
                                        hapticFeedback.performHapticFeedback(
                                            HapticFeedbackType.GestureEnd,
                                        )
                                    },
                                ),
                                reorderHandle = {
                                    Box(
                                        modifier = Modifier.size(48.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = NextIcons.DragHandle,
                                            contentDescription = stringResource(R.string.reorder_playlist_item),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = onPlay,
                                onRemove = {
                                    onAction(PlaylistDetailUiAction.ShowRemoveDialogFor(item))
                                },
                            )
                        }
                    } else {
                        PlaylistVideoRow(
                            item = item,
                            isFirstItem = index == 0,
                            isLastItem = index == visibleItems.lastIndex,
                            isTv = isTv,
                            isReordering = isReordering,
                            actionsEnabled = actionsEnabled,
                            onClick = onPlay,
                            onRemove = {
                                onAction(PlaylistDetailUiAction.ShowRemoveDialogFor(item))
                            },
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun RemoveVideoDialog(
    item: PlaylistItem,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    NextDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.remove_video)) },
        content = {
            Text(
                stringResource(
                    R.string.remove_video_confirmation,
                    item.video.displayName,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.tvFocusRing(),
            ) {
                Text(stringResource(R.string.remove))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.tvFocusRing(),
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaylistVideoRow(
    item: PlaylistItem,
    isFirstItem: Boolean,
    isLastItem: Boolean,
    isTv: Boolean,
    isReordering: Boolean,
    actionsEnabled: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    reorderHandle: (@Composable () -> Unit)? = null,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val rowFocusRequester = remember { FocusRequester() }
    val overflowFocusRequester = remember { FocusRequester() }

    NextSegmentedListItem(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(rowFocusRequester)
            .focusProperties {
                if (isTv) right = overflowFocusRequester
            },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        isFirstItem = isFirstItem,
        isLastItem = isLastItem,
        onClick = if (isReordering) {
            {}
        } else {
            onClick
        },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                reorderHandle?.invoke()
                Box(
                    modifier = Modifier
                        .width(min(100.dp, LocalConfiguration.current.screenWidthDp.dp * 0.30f))
                        .aspectRatio(16f / 10f)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
                ) {
                    Icon(
                        imageVector = NextIcons.Video,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surfaceColorAtElevation(100.dp),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxSize(0.5f),
                    )
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.video.uriString)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        },
        content = {
            Text(
                text = item.video.displayName,
                maxLines = 2,
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = item.video.parentPath,
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isReordering) {
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            enabled = actionsEnabled,
                            modifier = Modifier
                                .focusRequester(overflowFocusRequester)
                                .focusProperties {
                                    if (isTv) left = rowFocusRequester
                                }
                                .tvFocusRing(),
                        ) {
                            Icon(
                                imageVector = NextIcons.MoreVert,
                                contentDescription = stringResource(R.string.playlist_actions),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.remove)) },
                                leadingIcon = {
                                    Icon(NextIcons.Delete, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onRemove()
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun PlaylistEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.empty_playlist_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.empty_playlist_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PlaylistSearchEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = NextIcons.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.size(16.dp))
        Text(
            text = stringResource(R.string.no_matching_videos),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PlaylistUnavailable(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.playlist_unavailable),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
