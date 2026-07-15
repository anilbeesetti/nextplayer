package dev.anilbeesetti.nextplayer.feature.playlist.screens.list

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.components.NextSegmentedListItem
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.rememberTvListFocusRequester
import dev.anilbeesetti.nextplayer.core.ui.components.requestFocusUntilLanded
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.core.ui.components.tvListFocus
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.feature.playlist.R
import dev.anilbeesetti.nextplayer.feature.playlist.composables.AddM3uUrlDialog
import dev.anilbeesetti.nextplayer.feature.playlist.composables.PlaylistNameDialog
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class M3uDocument(
    val uri: String,
    val displayName: String,
)

@Composable
fun PlaylistListScreenRoute(
    onPlaylistClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: PlaylistListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingDocument by remember { mutableStateOf<M3uDocument?>(null) }
    val openDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val document = persistM3uDocument(
            uri = uri.toString(),
            fallbackDisplayName = uri.lastPathSegment.orEmpty(),
            persistPermission = {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            queryDisplayName = { context.contentResolver.queryDisplayName(uri) },
        )
        if (document == null) {
            Toast.makeText(context, R.string.document_permission_error, Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        pendingDocument = document
    }

    ObservePlaylistEvents(viewModel.events) { event ->
        when (event) {
            is PlaylistListEvent.Created -> onPlaylistClick(event.playlistId)
            is PlaylistListEvent.Message -> Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
        }
    }

    PlaylistListScreen(
        uiState = uiState,
        pendingDocument = pendingDocument,
        onDocumentConsumed = { pendingDocument = null },
        onPickM3uFile = {
            openDocumentLauncher.launch(
                arrayOf(
                    "application/vnd.apple.mpegurl",
                    "application/x-mpegURL",
                    "audio/mpegurl",
                    "audio/x-mpegurl",
                    "text/plain",
                    "application/octet-stream",
                    "*/*",
                ),
            )
        },
        onPlaylistClick = onPlaylistClick,
        onSettingsClick = onSettingsClick,
        onAction = viewModel::onAction,
    )
}

private enum class CreationDialog { NONE, EMPTY, URL, FILE }

@Composable
internal fun PlaylistListScreen(
    uiState: PlaylistListUiState,
    pendingDocument: M3uDocument? = null,
    onDocumentConsumed: () -> Unit = {},
    onPickM3uFile: () -> Unit = {},
    onPlaylistClick: (Long) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onAction: (PlaylistListAction) -> Unit = {},
) {
    var showCreationChooser by rememberSaveable { mutableStateOf(false) }
    var creationDialog by rememberSaveable { mutableStateOf(CreationDialog.NONE) }
    var fileSource by rememberSaveable { mutableStateOf("") }
    var fileDisplayName by rememberSaveable { mutableStateOf("") }
    var playlistToDelete by remember { mutableStateOf<PlaylistSummary?>(null) }
    val context = LocalContext.current
    val isTv = remember { context.isTelevision }
    val showEmptyState = uiState.playlists.isEmpty() && !uiState.isLoading
    val createFocusRequester = remember { FocusRequester() }

    LaunchedEffect(pendingDocument) {
        pendingDocument?.let { document ->
            fileSource = document.uri
            fileDisplayName = document.displayName
            creationDialog = CreationDialog.FILE
            onDocumentConsumed()
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
                        Icon(NextIcons.Settings, contentDescription = stringResource(dev.anilbeesetti.nextplayer.core.ui.R.string.settings))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    onAction(PlaylistListAction.ClearFormError)
                    showCreationChooser = true
                },
                icon = {
                    Icon(NextIcons.Add, contentDescription = stringResource(R.string.create_playlist))
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
                uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                showEmptyState -> PlaylistEmptyState(Modifier.fillMaxSize())
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
                    itemsIndexed(uiState.playlists, key = { _, playlist -> playlist.id }) { index, playlist ->
                        PlaylistItem(
                            playlist = playlist,
                            isFirstItem = index == 0,
                            isLastItem = index == uiState.playlists.lastIndex,
                            onClick = { onPlaylistClick(playlist.id) },
                            onDelete = { playlistToDelete = playlist },
                        )
                    }
                }
            }
        }
    }

    if (showCreationChooser) {
        CreationChooserDialog(
            onDismissRequest = { showCreationChooser = false },
            onCreateEmpty = {
                showCreationChooser = false
                creationDialog = CreationDialog.EMPTY
            },
            onCreateUrl = {
                showCreationChooser = false
                creationDialog = CreationDialog.URL
            },
            onCreateFile = {
                showCreationChooser = false
                onPickM3uFile()
            },
        )
    }

    when (creationDialog) {
        CreationDialog.EMPTY -> PlaylistNameDialog(
            title = stringResource(R.string.create_empty_playlist),
            isSaving = uiState.isSaving,
            error = uiState.formError,
            onDismissRequest = { dismissCreationDialog(onAction) { creationDialog = CreationDialog.NONE } },
            onConfirm = { onAction(PlaylistListAction.CreateEditable(it)) },
            onClearError = { onAction(PlaylistListAction.ClearFormError) },
        )
        CreationDialog.URL -> AddM3uUrlDialog(
            isSaving = uiState.isSaving,
            error = uiState.formError,
            onDismissRequest = { dismissCreationDialog(onAction) { creationDialog = CreationDialog.NONE } },
            onConfirm = { name, url ->
                onAction(PlaylistListAction.CreateLinked(name, PlaylistType.M3U_URL, url))
            },
            onClearError = { onAction(PlaylistListAction.ClearFormError) },
        )
        CreationDialog.FILE -> PlaylistNameDialog(
            title = stringResource(R.string.add_m3u_file_playlist),
            initialName = fileDisplayName.withoutM3uExtension(),
            chosenDocument = fileDisplayName,
            isSaving = uiState.isSaving,
            error = uiState.formError,
            onDismissRequest = { dismissCreationDialog(onAction) { creationDialog = CreationDialog.NONE } },
            onConfirm = { name ->
                onAction(PlaylistListAction.CreateLinked(name, PlaylistType.M3U_FILE, fileSource))
            },
            onClearError = { onAction(PlaylistListAction.ClearFormError) },
        )
        CreationDialog.NONE -> Unit
    }

    playlistToDelete?.let { playlist ->
        NextDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text(stringResource(R.string.delete_playlist)) },
            content = { Text(stringResource(R.string.delete_playlist_confirmation, playlist.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAction(PlaylistListAction.Delete(playlist.id))
                        playlistToDelete = null
                    },
                    modifier = Modifier.tvFocusRing(),
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }, modifier = Modifier.tvFocusRing()) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun CreationChooserDialog(
    onDismissRequest: () -> Unit,
    onCreateEmpty: () -> Unit,
    onCreateUrl: () -> Unit,
    onCreateFile: () -> Unit,
) {
    NextDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.choose_playlist_type)) },
        content = {
            Column {
                CreationChoice(stringResource(R.string.create_empty_playlist), NextIcons.Add, onCreateEmpty)
                CreationChoice(stringResource(R.string.add_m3u_url_playlist), NextIcons.Link, onCreateUrl)
                CreationChoice(stringResource(R.string.add_m3u_file_playlist), NextIcons.FileOpen, onCreateFile)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest, modifier = Modifier.tvFocusRing()) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun CreationChoice(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusRing(),
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text(text, modifier = Modifier.weight(1f))
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun PlaylistItem(
    playlist: PlaylistSummary,
    isFirstItem: Boolean,
    isLastItem: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    val itemCount = pluralStringResource(R.plurals.playlist_item_count, playlist.itemCount, playlist.itemCount)
    val sourceStatus = playlist.sourceStatus()

    NextSegmentedListItem(
        isFirstItem = isFirstItem,
        isLastItem = isLastItem,
        onClick = onClick,
        contentPadding = PaddingValues(12.dp),
        leadingContent = {
            Icon(
                imageVector = when (playlist.type) {
                    PlaylistType.EDITABLE -> NextIcons.Edit
                    PlaylistType.M3U_URL -> NextIcons.Link
                    PlaylistType.M3U_FILE -> NextIcons.FileOpen
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        },
        overlineContent = { Text(playlist.typeLabel()) },
        content = {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = "$itemCount · $sourceStatus",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(NextIcons.ExtraSettings, contentDescription = stringResource(R.string.playlist_actions))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
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
private fun PlaylistSummary.typeLabel(): String = stringResource(
    when (type) {
        PlaylistType.EDITABLE -> R.string.editable_playlist
        PlaylistType.M3U_URL -> R.string.m3u_url_playlist
        PlaylistType.M3U_FILE -> R.string.m3u_file_playlist
    },
)

@Composable
private fun PlaylistSummary.sourceStatus(): String {
    if (type == PlaylistType.EDITABLE) return stringResource(R.string.editable_playlist)
    val refreshedAt = lastRefreshedAt ?: return stringResource(R.string.not_refreshed)
    val context = LocalContext.current
    val formatted = remember(refreshedAt, context) {
        val date = Date(refreshedAt)
        "${DateFormat.getMediumDateFormat(context).format(date)} ${DateFormat.getTimeFormat(context).format(date)}"
    }
    return stringResource(R.string.last_refreshed, formatted)
}

@Composable
private fun PlaylistEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = NextIcons.Link,
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

private fun dismissCreationDialog(
    onAction: (PlaylistListAction) -> Unit,
    dismiss: () -> Unit,
) {
    onAction(PlaylistListAction.ClearFormError)
    dismiss()
}

internal fun String.withoutM3uExtension(): String = replace(Regex("(?i)\\.m3u8?$"), "")

internal fun persistM3uDocument(
    uri: String,
    fallbackDisplayName: String,
    persistPermission: () -> Unit,
    queryDisplayName: () -> String?,
): M3uDocument? {
    runCatching(persistPermission).getOrElse { return null }
    val displayName = runCatching(queryDisplayName).getOrNull()
        ?.takeIf(String::isNotBlank)
        ?: fallbackDisplayName
    return M3uDocument(uri = uri, displayName = displayName)
}

private fun android.content.ContentResolver.queryDisplayName(uri: Uri): String? {
    var cursor: Cursor? = null
    return try {
        cursor = query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        if (cursor?.moveToFirst() == true) {
            cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        } else {
            null
        }
    } finally {
        cursor?.close()
    }
}

@Composable
private fun <T> ObservePlaylistEvents(flow: Flow<T>, onEvent: (T) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(flow, lifecycleOwner.lifecycle) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            withContext(Dispatchers.Main.immediate) { flow.collect(onEvent) }
        }
    }
}
