package dev.anilbeesetti.nextplayer.feature.playlist.screens.list

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.components.NextSegmentedListItem
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.rememberTvListFocusRequester
import dev.anilbeesetti.nextplayer.core.ui.components.requestFocusUntilLanded
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.core.ui.components.tvListFocus
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons

@Composable
fun PlaylistListScreenRoute(
    onPlaylistClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: PlaylistListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.synchronize()
    }
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is PlaylistListEvent.Created -> onPlaylistClick(event.playlistId)
                is PlaylistListEvent.Message ->
                    Toast.makeText(context, event.messageRes, Toast.LENGTH_SHORT).show()
            }
        }
    }

    PlaylistListScreen(
        uiState = uiState,
        onPlaylistClick = onPlaylistClick,
        onSettingsClick = onSettingsClick,
        onCreate = viewModel::create,
        onRename = viewModel::rename,
        onDelete = viewModel::delete,
        onClearError = viewModel::clearFormError,
    )
}

@Composable
internal fun PlaylistListScreen(
    uiState: PlaylistListUiState,
    onPlaylistClick: (Long) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onCreate: (String) -> Unit = {},
    onRename: (Long, String) -> Unit = { _, _ -> },
    onDelete: (Long) -> Unit = {},
    onClearError: () -> Unit = {},
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<PlaylistSummary?>(null) }
    var playlistToDelete by remember { mutableStateOf<PlaylistSummary?>(null) }
    val context = LocalContext.current
    val isTv = remember(context) { context.isTelevision }
    val showEmptyState = uiState.playlists.isEmpty() && !uiState.isLoading
    val createFocusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.saveVersion) {
        if (uiState.saveVersion > 0) {
            showCreateDialog = false
            playlistToRename = null
        }
    }
    if (isTv) {
        LaunchedEffect(showEmptyState) {
            if (showEmptyState) createFocusRequester.requestFocusUntilLanded()
        }
    }

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(R.string.playlists),
                fontWeight = FontWeight.Bold,
                actions = {
                    IconButton(onClick = onSettingsClick, modifier = Modifier.tvFocusRing()) {
                        Icon(
                            imageVector = NextIcons.Settings,
                            contentDescription = stringResource(R.string.settings),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    onClearError()
                    showCreateDialog = true
                },
                icon = {
                    Icon(
                        imageVector = NextIcons.Add,
                        contentDescription = stringResource(R.string.create_playlist),
                    )
                },
                text = { Text(stringResource(R.string.create_playlist)) },
                modifier = Modifier
                    .focusRequester(createFocusRequester)
                    .tvFocusRing(shape = RoundedCornerShape(16.dp)),
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

        Box(modifier = containerModifier) {
            when {
                uiState.isLoading && uiState.playlists.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                showEmptyState -> PlaylistListEmptyState(Modifier.fillMaxSize())
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .tvListFocus(rememberTvListFocusRequester()),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        top = 8.dp,
                        end = 8.dp,
                        bottom = padding.calculateBottomPadding() + 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(
                        items = uiState.playlists,
                        key = { _, playlist -> playlist.id },
                    ) { index, playlist ->
                        PlaylistRow(
                            playlist = playlist,
                            isFirstItem = index == 0,
                            isLastItem = index == uiState.playlists.lastIndex,
                            onClick = { onPlaylistClick(playlist.id) },
                            onRename = {
                                onClearError()
                                playlistToRename = playlist
                            },
                            onDelete = { playlistToDelete = playlist },
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        PlaylistNameDialog(
            title = stringResource(R.string.create_playlist),
            confirmLabel = stringResource(R.string.create),
            isSaving = uiState.isSaving,
            error = uiState.formErrorRes?.let { stringResource(it) },
            onDismissRequest = {
                if (!uiState.isSaving) {
                    showCreateDialog = false
                    onClearError()
                }
            },
            onConfirm = onCreate,
            onClearError = onClearError,
        )
    }

    playlistToRename?.let { playlist ->
        PlaylistNameDialog(
            title = stringResource(R.string.rename_playlist),
            confirmLabel = stringResource(R.string.save),
            initialName = playlist.name,
            isSaving = uiState.isSaving,
            error = uiState.formErrorRes?.let { stringResource(it) },
            onDismissRequest = {
                if (!uiState.isSaving) {
                    playlistToRename = null
                    onClearError()
                }
            },
            onConfirm = { onRename(playlist.id, it) },
            onClearError = onClearError,
        )
    }

    playlistToDelete?.let { playlist ->
        NextDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text(stringResource(R.string.delete_playlist)) },
            content = {
                Text(stringResource(R.string.delete_playlist_confirmation, playlist.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(playlist.id)
                        playlistToDelete = null
                    },
                    modifier = Modifier.tvFocusRing(),
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { playlistToDelete = null },
                    modifier = Modifier.tvFocusRing(),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun PlaylistNameDialog(
    title: String,
    confirmLabel: String,
    isSaving: Boolean,
    error: String?,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
    onClearError: () -> Unit,
    initialName: String = "",
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var attemptedSubmit by rememberSaveable { mutableStateOf(false) }
    val trimmedName = name.trim()

    NextDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        onClearError()
                    },
                    label = { Text(stringResource(R.string.playlist_name)) },
                    enabled = !isSaving,
                    singleLine = true,
                    isError = error != null || attemptedSubmit && trimmedName.isEmpty(),
                    supportingText = {
                        when {
                            error != null -> Text(error)
                            attemptedSubmit && trimmedName.isEmpty() ->
                                Text(stringResource(R.string.name_required))
                        }
                    },
                )
                if (isSaving) {
                    Text(
                        text = stringResource(R.string.saving_playlist),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    attemptedSubmit = true
                    if (trimmedName.isNotEmpty()) onConfirm(trimmedName)
                },
                enabled = !isSaving && trimmedName.isNotEmpty(),
                modifier = Modifier.tvFocusRing(),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                enabled = !isSaving,
                modifier = Modifier.tvFocusRing(),
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaylistRow(
    playlist: PlaylistSummary,
    isFirstItem: Boolean,
    isLastItem: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    val videoCount = pluralStringResource(
        R.plurals.playlist_video_count,
        playlist.itemCount,
        playlist.itemCount,
    )

    NextSegmentedListItem(
        isFirstItem = isFirstItem,
        isLastItem = isLastItem,
        onClick = onClick,
        contentPadding = PaddingValues(12.dp),
        leadingContent = {
            Icon(
                imageVector = NextIcons.Playlist,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        },
        content = {
            Text(
                text = playlist.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = "${stringResource(R.string.local_playlist)} · $videoCount",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.tvFocusRing(),
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
                        text = { Text(stringResource(R.string.rename_playlist)) },
                        leadingIcon = { Icon(NextIcons.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        leadingIcon = { Icon(NextIcons.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun PlaylistListEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = NextIcons.Playlist,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.size(16.dp))
        Text(
            text = stringResource(R.string.no_playlists_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.no_playlists_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
