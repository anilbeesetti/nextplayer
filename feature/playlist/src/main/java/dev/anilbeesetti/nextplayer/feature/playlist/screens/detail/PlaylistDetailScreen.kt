package dev.anilbeesetti.nextplayer.feature.playlist.screens.detail

import android.content.res.Configuration
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.model.Playlist
import dev.anilbeesetti.nextplayer.core.model.PlaylistItem
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.components.NextSegmentedListItem
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.rememberTvListFocusRequester
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.core.ui.components.tvListFocus
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.feature.playlist.R
import java.util.Date
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import sh.calvin.reorderable.DragGestureDetector
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import dev.anilbeesetti.nextplayer.core.ui.R as CoreUiR

@Composable
fun PlaylistDetailScreenRoute(
    onNavigateUp: () -> Unit,
    onPlayPlaylist: (uris: List<Uri>, startUri: Uri) -> Unit,
    viewModel: PlaylistDetailViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboard = LocalConfiguration.current.keyboard
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    ObservePlaylistDetailEvents(viewModel.events) { event ->
        when (event) {
            is PlaylistDetailEvent.Play -> onPlayPlaylist(
                event.uris.map(String::toUri),
                event.startUri.toUri(),
            )
            is PlaylistDetailEvent.Message -> coroutineScope.launch {
                snackbarHostState.showSnackbar(event.text)
            }
            PlaylistDetailEvent.Deleted -> onNavigateUp()
        }
    }

    PlaylistDetailScreen(
        uiState = uiState,
        isTv = context.isTelevision ||
            keyboard == Configuration.KEYBOARD_QWERTY ||
            keyboard == Configuration.KEYBOARD_12KEY,
        snackbarHostState = snackbarHostState,
        onBack = onNavigateUp,
        onAction = viewModel::onAction,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlaylistDetailScreen(
    uiState: PlaylistDetailUiState,
    isTv: Boolean,
    onBack: () -> Unit,
    onAction: (PlaylistDetailAction) -> Unit,
    snackbarHostState: SnackbarHostState? = null,
) {
    val ownedSnackbarHostState = remember { SnackbarHostState() }
    val resolvedSnackbarHostState = snackbarHostState ?: ownedSnackbarHostState
    val playlist = uiState.playlist
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = playlist?.name.orEmpty(),
                fontWeight = FontWeight.Bold,
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBack, modifier = Modifier.tvFocusRing()) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(CoreUiR.string.navigate_up),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onAction(PlaylistDetailAction.PlayAll) },
                        enabled = playlist?.items?.isNotEmpty() == true && !uiState.isMoving,
                        modifier = Modifier.tvFocusRing(),
                    ) {
                        Icon(
                            imageVector = NextIcons.Play,
                            contentDescription = stringResource(R.string.play_all),
                        )
                    }
                    if (playlist?.type?.isLinked == true) {
                        if (uiState.isRefreshing) {
                            val refreshingDescription = stringResource(R.string.refreshing_playlist)
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .size(24.dp)
                                    .semantics { contentDescription = refreshingDescription },
                                strokeWidth = 2.dp,
                            )
                        } else {
                            IconButton(
                                onClick = { onAction(PlaylistDetailAction.Refresh) },
                                modifier = Modifier.tvFocusRing(),
                            ) {
                                Icon(
                                    imageVector = NextIcons.Update,
                                    contentDescription = stringResource(R.string.refresh_playlist),
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { showDeleteConfirmation = true },
                        enabled = playlist != null,
                        modifier = Modifier.tvFocusRing(),
                    ) {
                        Icon(
                            imageVector = NextIcons.Delete,
                            contentDescription = stringResource(R.string.delete_playlist),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(resolvedSnackbarHostState) },
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
            playlist.type.isLinked -> PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { onAction(PlaylistDetailAction.Refresh) },
                modifier = containerModifier,
            ) {
                PlaylistDetailContent(playlist, isTv, uiState.isMoving, padding, onAction)
            }
            else -> Box(containerModifier) {
                PlaylistDetailContent(playlist, isTv, uiState.isMoving, padding, onAction)
            }
        }
    }

    if (showDeleteConfirmation && playlist != null) {
        NextDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.delete_playlist)) },
            content = { Text(stringResource(R.string.delete_playlist_confirmation, playlist.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onAction(PlaylistDetailAction.Delete)
                    },
                    modifier = Modifier.tvFocusRing(),
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false },
                    modifier = Modifier.tvFocusRing(),
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun PlaylistDetailContent(
    playlist: Playlist,
    isTv: Boolean,
    isMoving: Boolean,
    scaffoldPadding: PaddingValues,
    onAction: (PlaylistDetailAction) -> Unit,
) {
    val displayedItems = playlist.items
    val lazyListState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current

    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onAction(PlaylistDetailAction.PreviewMove(from.index, to.index))
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    Column(Modifier.fillMaxSize()) {
        if (playlist.type.isLinked) {
            Text(
                text = playlist.lastRefreshedAt.refreshStatus(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        if (displayedItems.isEmpty()) {
            PlaylistEmptyState(Modifier.fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .tvListFocus(rememberTvListFocusRequester()),
                state = lazyListState,
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
                    key = { _, item -> item.uriString },
                ) { index, item ->
                    if (playlist.type == PlaylistType.EDITABLE && !isTv && !isMoving) {
                        ReorderableItem(state = reorderState, key = item.uriString) {
                            PlaylistItemRow(
                                item = item,
                                isFirstItem = index == 0,
                                isLastItem = index == displayedItems.lastIndex,
                                reorderHandle = {
                                    Icon(
                                        imageVector = NextIcons.Move,
                                        contentDescription = stringResource(R.string.reorder_playlist_item),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .draggableHandle(
                                                dragGestureDetector = DragGestureDetector.LongPress,
                                                onDragStarted = {
                                                    onAction(PlaylistDetailAction.StartMoveDrag)
                                                    hapticFeedback.performHapticFeedback(
                                                        HapticFeedbackType.GestureThresholdActivate,
                                                    )
                                                },
                                                onDragStopped = {
                                                    onAction(PlaylistDetailAction.StopMoveDrag)
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                                },
                                            )
                                            .padding(8.dp),
                                    )
                                },
                                onClick = { onAction(PlaylistDetailAction.PlayItem(item.uriString)) },
                            )
                        }
                    } else {
                        PlaylistItemRow(
                            item = item,
                            isFirstItem = index == 0,
                            isLastItem = index == displayedItems.lastIndex,
                            canMoveUp = playlist.type == PlaylistType.EDITABLE && !isMoving && index > 0,
                            canMoveDown = playlist.type == PlaylistType.EDITABLE && !isMoving &&
                                index < displayedItems.lastIndex,
                            showMoveButtons = playlist.type == PlaylistType.EDITABLE && isTv && !isMoving,
                            onMoveUp = {
                                onAction(PlaylistDetailAction.MoveItem(item.uriString, index - 1))
                            },
                            onMoveDown = {
                                onAction(PlaylistDetailAction.MoveItem(item.uriString, index + 1))
                            },
                            onClick = { onAction(PlaylistDetailAction.PlayItem(item.uriString)) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaylistItemRow(
    item: PlaylistItem,
    isFirstItem: Boolean,
    isLastItem: Boolean,
    reorderHandle: (@Composable () -> Unit)? = null,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    showMoveButtons: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    NextSegmentedListItem(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        interactionSource = interactionSource,
        isFirstItem = isFirstItem,
        isLastItem = isLastItem,
        onClick = onClick,
        leadingContent = reorderHandle,
        content = {
            Text(
                text = item.title?.takeIf(String::isNotBlank)
                    ?: item.uriString.substringAfterLast('/').takeIf(String::isNotBlank)
                    ?: stringResource(R.string.unknown_playlist_item),
                maxLines = 2,
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = item.uriString,
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = if (showMoveButtons) {
            {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = canMoveUp,
                        modifier = Modifier.tvFocusRing(),
                    ) {
                        Icon(
                            imageVector = NextIcons.ArrowUpward,
                            contentDescription = stringResource(R.string.move_up),
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = canMoveDown,
                        modifier = Modifier.tvFocusRing(),
                    ) {
                        Icon(
                            imageVector = NextIcons.ArrowDownward,
                            contentDescription = stringResource(R.string.move_down),
                        )
                    }
                }
            }
        } else {
            null
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

@Composable
private fun Long?.refreshStatus(): String = if (this == null) {
    stringResource(R.string.not_refreshed)
} else {
    val context = LocalContext.current
    val formatted = remember(this) {
        DateFormat.getMediumDateFormat(context).format(Date(this))
    }
    stringResource(R.string.last_refreshed, formatted)
}

@Composable
private fun <T> ObservePlaylistDetailEvents(flow: Flow<T>, onEvent: (T) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(flow, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect(currentOnEvent)
        }
    }
}

private val PlaylistType.isLinked: Boolean
    get() = this == PlaylistType.M3U_URL || this == PlaylistType.M3U_FILE
