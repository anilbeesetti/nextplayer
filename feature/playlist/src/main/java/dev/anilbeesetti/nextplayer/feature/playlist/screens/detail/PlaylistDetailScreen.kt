package dev.anilbeesetti.nextplayer.feature.playlist.screens.detail

import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.components.NextSegmentedListItem
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.rememberTvListFocusRequester
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.core.ui.components.tvListFocus
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import sh.calvin.reorderable.DragGestureDetector
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PlaylistDetailScreenRoute(
    onNavigateUp: () -> Unit,
    onPlayVideos: (uris: List<Uri>, startUri: Uri) -> Unit,
    viewModel: PlaylistDetailViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.synchronize()
    }
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is PlaylistDetailEvent.Message ->
                    Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    PlaylistDetailScreen(
        uiState = uiState,
        isTv = context.isTelevision,
        onBack = onNavigateUp,
        onPlayVideos = onPlayVideos,
        onRemoveVideo = viewModel::removeVideo,
        onReplaceOrder = viewModel::replaceOrder,
    )
}

@Composable
internal fun PlaylistDetailScreen(
    uiState: PlaylistDetailUiState,
    isTv: Boolean,
    onBack: () -> Unit,
    onPlayVideos: (uris: List<Uri>, startUri: Uri) -> Unit,
    onRemoveVideo: (String) -> Unit,
    onReplaceOrder: (List<String>) -> Unit,
) {
    val playlist = uiState.playlist
    val videoUris = playlist?.items.orEmpty().map { it.video.uriString.toUri() }
    var isReordering by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(playlist?.items?.size) {
        if ((playlist?.items?.size ?: 0) < 2) {
            isReordering = false
        }
    }
    LaunchedEffect(isTv) {
        if (isTv) isReordering = false
    }

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = playlist?.name.orEmpty(),
                fontWeight = FontWeight.Bold,
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        modifier = Modifier.tvFocusRing(),
                    ) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
                actions = {
                    if (isReordering) {
                        IconButton(
                            onClick = { isReordering = false },
                            enabled = uiState.actionsEnabled,
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
                                videoUris.firstOrNull()?.let { startUri ->
                                    onPlayVideos(videoUris, startUri)
                                }
                            },
                            enabled = videoUris.isNotEmpty() && uiState.actionsEnabled,
                            modifier = Modifier.tvFocusRing(),
                        ) {
                            Icon(
                                imageVector = NextIcons.Play,
                                contentDescription = stringResource(R.string.play_all),
                            )
                        }
                        if (!isTv) {
                            IconButton(
                                onClick = { isReordering = true },
                                enabled = videoUris.size > 1 && uiState.actionsEnabled,
                                modifier = Modifier.tvFocusRing(),
                            ) {
                                Icon(
                                    imageVector = NextIcons.Reorder,
                                    contentDescription = stringResource(R.string.reorder_playlist),
                                )
                            }
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { padding ->
        val containerModifier = Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding())
            .padding(start = padding.calculateStartPadding(LocalLayoutDirection.current) + 2.dp)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(MaterialTheme.colorScheme.background)

        when {
            uiState.isLoading -> Box(containerModifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            playlist == null -> PlaylistUnavailable(containerModifier)
            else -> PlaylistDetailContent(
                playlist = playlist,
                isTv = isTv,
                isReordering = isReordering,
                actionsEnabled = uiState.actionsEnabled,
                scaffoldPadding = padding,
                onPlayVideos = onPlayVideos,
                onRemoveVideo = onRemoveVideo,
                onReplaceOrder = onReplaceOrder,
                modifier = containerModifier,
            )
        }
    }
}

@Composable
private fun PlaylistDetailContent(
    playlist: Playlist,
    isTv: Boolean,
    isReordering: Boolean,
    actionsEnabled: Boolean,
    scaffoldPadding: PaddingValues,
    onPlayVideos: (uris: List<Uri>, startUri: Uri) -> Unit,
    onRemoveVideo: (String) -> Unit,
    onReplaceOrder: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var displayedItems by remember { mutableStateOf(playlist.items) }
    var isDragging by remember { mutableStateOf(false) }
    var itemToRemove by remember { mutableStateOf<PlaylistItem?>(null) }
    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(playlist.items, isDragging) {
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
                    bottom = scaffoldPadding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(
                    items = displayedItems,
                    key = { _, item -> item.video.uriString },
                ) { index, item ->
                    val onPlay = {
                        val uris = displayedItems.map { it.video.uriString.toUri() }
                        onPlayVideos(uris, item.video.uriString.toUri())
                    }
                    if (isReordering && !isTv) {
                        ReorderableItem(
                            state = reorderState,
                            key = item.video.uriString,
                        ) {
                            PlaylistVideoRow(
                                item = item,
                                isFirstItem = index == 0,
                                isLastItem = index == displayedItems.lastIndex,
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
                                        onReplaceOrder(
                                            displayedItems.map { it.video.uriString },
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
                                onRemove = { itemToRemove = item },
                            )
                        }
                    } else {
                        PlaylistVideoRow(
                            item = item,
                            isFirstItem = index == 0,
                            isLastItem = index == displayedItems.lastIndex,
                            isTv = isTv,
                            isReordering = isReordering,
                            actionsEnabled = actionsEnabled,
                            onClick = onPlay,
                            onRemove = { itemToRemove = item },
                        )
                    }
                }
            }
        }
    }

    itemToRemove?.let { item ->
        NextDialog(
            onDismissRequest = { itemToRemove = null },
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
                    onClick = {
                        onRemoveVideo(item.video.uriString)
                        itemToRemove = null
                    },
                    modifier = Modifier.tvFocusRing(),
                ) {
                    Text(stringResource(R.string.remove))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { itemToRemove = null },
                    modifier = Modifier.tvFocusRing(),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
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
private fun PlaylistUnavailable(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.playlist_unavailable),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
