package dev.anilbeesetti.nextplayer.feature.playlist.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.model.Playlist
import dev.anilbeesetti.nextplayer.core.model.PlaylistItem
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.components.NextOutlinedTextField
import dev.anilbeesetti.nextplayer.core.ui.components.NextSegmentedListItem
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.rememberTvListFocusRequester
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.core.ui.components.tvListFocus
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.extensions.copy
import java.text.DateFormat
import java.util.Date
import sh.calvin.reorderable.DragGestureDetector
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PlaylistDetailScreen(
    viewModel: PlaylistDetailViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    PlaylistDetailScreenContent(
        state = state,
        onAction = viewModel::onAction,
    )
}

@Composable
internal fun PlaylistDetailScreenContent(
    state: PlaylistDetailUiState,
    onAction: (PlaylistDetailUiAction) -> Unit = {},
) {
    val isTv = LocalContext.current.isTelevision
    val playlist = (state.playlistDataState as? DataState.Success)?.value
    val videoUris = playlist?.items.orEmpty().map { it.uri.toUri() }
    val playbackStartUri = playlist?.lastPlayedItem
        ?.uri
        ?.toUri()
        ?: videoUris.firstOrNull()
    val isReordering = state.isReordering &&
        playlist?.type == PlaylistType.LOCAL &&
        !isTv
    val searchFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = rememberTvListFocusRequester()
    val keyboardController = LocalSoftwareKeyboardController.current
    val exitSearch: () -> Unit = {
        onAction(PlaylistDetailUiAction.OnCloseSearchClick)
        keyboardController?.hide()
    }

    LaunchedEffect(state.isSearching) {
        if (state.isSearching) searchFocusRequester.requestFocus()
    }
    LaunchedEffect(isTv, state.isReordering) {
        if (isTv && state.isReordering) {
            onAction(PlaylistDetailUiAction.OnFinishReorderingClick)
        }
    }
    BackHandler(enabled = state.isSearching, onBack = exitSearch)

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = {
                    if (state.isSearching) {
                        NextOutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = {
                                onAction(PlaylistDetailUiAction.OnSearchQueryChange(it))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester)
                                .tvFocusRing(shape = CircleShape),
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
                    if (!state.isSearching) {
                        if (isReordering) {
                            IconButton(
                                onClick = {
                                    onAction(PlaylistDetailUiAction.OnFinishReorderingClick)
                                },
                                enabled = !state.updateActionState.isRunning,
                                modifier = Modifier.tvFocusRing(),
                            ) {
                                Icon(
                                    imageVector = NextIcons.Check,
                                    contentDescription = stringResource(R.string.finish_reordering),
                                )
                            }
                        } else {
                            if (playlist?.type != null && playlist.type != PlaylistType.LOCAL) {
                                if (state.isRefreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .size(24.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    IconButton(
                                        onClick = { onAction(PlaylistDetailUiAction.Refresh) },
                                        modifier = Modifier.tvFocusRing(),
                                    ) {
                                        Icon(
                                            imageVector = NextIcons.Update,
                                            contentDescription = stringResource(
                                                R.string.refresh_playlist,
                                            ),
                                        )
                                    }
                                }
                            }
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
                            if (!isTv && playlist?.type == PlaylistType.LOCAL) {
                                IconButton(
                                    onClick = {
                                        onAction(PlaylistDetailUiAction.OnReorderClick)
                                    },
                                    enabled = videoUris.size > 1 &&
                                        !state.updateActionState.isRunning,
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
            if (!state.isSearching && !isReordering && playbackStartUri != null) {
                FloatingActionButton(
                    onClick = {
                        onAction(
                            PlaylistDetailUiAction.OnPlay(playbackStartUri),
                        )
                    },
                    modifier = Modifier
                        .tvFocusRing(shape = MaterialTheme.shapes.large)
                        .focusProperties { if (isTv) up = contentFocusRequester },
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
    ) { scaffoldPadding ->
        val containerModifier = Modifier
            .fillMaxSize()
            .padding(scaffoldPadding.copy(bottom = 0.dp))
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(MaterialTheme.colorScheme.background)

        when (state.playlistDataState) {
            DataState.Loading ->
                Box(containerModifier, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            is DataState.Error -> PlaylistUnavailable(containerModifier)

            is DataState.Success -> {
                if (playlist == null) {
                    PlaylistUnavailable(containerModifier)
                } else {
                    val content: @Composable (Modifier) -> Unit = { modifier ->
                        PlaylistDetailContent(
                            playlist = playlist,
                            contentFocusRequester = contentFocusRequester,
                            isTv = isTv,
                            isReordering = isReordering,
                            searchQuery = state.searchQuery,
                            showPlayFab = !state.isSearching &&
                                !isReordering &&
                                playbackStartUri != null,
                            actionsEnabled = !state.updateActionState.isRunning,
                            scaffoldPadding = scaffoldPadding,
                            onAction = onAction,
                            modifier = modifier,
                        )
                    }
                    if (playlist.type == PlaylistType.LOCAL) {
                        content(containerModifier)
                    } else {
                        PullToRefreshBox(
                            isRefreshing = state.isRefreshing,
                            onRefresh = { onAction(PlaylistDetailUiAction.Refresh) },
                            modifier = containerModifier,
                        ) {
                            content(Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }

    state.showRemoveDialogFor?.let { item ->
        RemoveVideoDialog(
            item = item,
            onConfirm = {
                onAction(PlaylistDetailUiAction.RemoveVideo(item.uri))
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
    contentFocusRequester: FocusRequester,
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
                item.displayTitle.contains(query, ignoreCase = true) ||
                    item.supportingText.contains(query, ignoreCase = true)
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

    Column(modifier = modifier) {
        if (playlist.type != PlaylistType.LOCAL) {
            playlist.lastRefreshedAt?.let { refreshedAt ->
                Text(
                    text = stringResource(
                        R.string.last_refreshed,
                        DateFormat.getDateTimeInstance().format(Date(refreshedAt)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            if (displayedItems.isEmpty()) {
                PlaylistEmptyState(Modifier.fillMaxSize())
            } else if (visibleItems.isEmpty()) {
                PlaylistSearchEmptyState(Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .tvListFocus(contentFocusRequester),
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
                        key = { _, item -> item.uri },
                    ) { index, item ->
                        val onPlay = {
                            onAction(PlaylistDetailUiAction.OnPlay(item.uri.toUri()))
                        }
                        if (isReordering && !isTv) {
                            ReorderableItem(
                                state = reorderState,
                                key = item.uri,
                            ) {
                                PlaylistVideoRow(
                                    item = item,
                                    isFirstItem = index == 0,
                                    isLastItem = index == visibleItems.lastIndex,
                                    isTv = false,
                                    isReordering = true,
                                    isEditable = true,
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
                                                    displayedItems.map { it.uri },
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
                                                contentDescription = stringResource(
                                                    R.string.reorder_playlist_item,
                                                ),
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
                                isEditable = playlist.type == PlaylistType.LOCAL,
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
                    item.displayTitle,
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
    isEditable: Boolean,
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
                            .data(item.tvgLogo ?: item.video?.uriString ?: item.uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = if (item.video != null) {
                            ContentScale.Crop
                        } else {
                            ContentScale.Fit
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        },
        content = {
            Text(
                text = item.displayTitle,
                maxLines = 2,
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = item.supportingText,
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
                if (!isReordering && isEditable) {
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
