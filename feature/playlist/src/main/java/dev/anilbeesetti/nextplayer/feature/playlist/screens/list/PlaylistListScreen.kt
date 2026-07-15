package dev.anilbeesetti.nextplayer.feature.playlist.screens.list

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistFileGrant
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class M3uDocument(
    val uri: String,
    val displayName: String,
    val grant: PlaylistFileGrant,
)

internal enum class CreationDialog { NONE, CHOOSER, EMPTY, URL, FILE }

@JvmInline
internal value class M3uFileRequest internal constructor(internal val token: Long)

internal class PlaylistCreationCoordinator(
    private val coroutineScope: CoroutineScope,
    private val onPreparationError: () -> Unit,
    private val onReleaseGrant: (PlaylistFileGrant) -> Unit,
) {
    var dialog by mutableStateOf(CreationDialog.NONE)
        private set

    var fileDocument by mutableStateOf<M3uDocument?>(null)
        private set

    private var activePreparation: Job? = null
    private var requestToken = 0L

    fun openChooser() {
        invalidateFilePreparation()
        clearFileDocument()
        dialog = CreationDialog.CHOOSER
    }

    fun chooseEmpty() {
        invalidateFilePreparation()
        clearFileDocument()
        dialog = CreationDialog.EMPTY
    }

    fun chooseUrl() {
        invalidateFilePreparation()
        clearFileDocument()
        dialog = CreationDialog.URL
    }

    fun chooseFile(onPickM3uFile: (M3uFileRequest) -> Unit) {
        invalidateFilePreparation()
        clearFileDocument()
        dialog = CreationDialog.NONE
        onPickM3uFile(M3uFileRequest(requestToken))
    }

    fun dismiss() {
        invalidateFilePreparation()
        clearFileDocument()
        dialog = CreationDialog.NONE
    }

    fun prepareFile(
        request: M3uFileRequest,
        prepare: suspend () -> M3uDocument?,
    ) {
        if (request.token != requestToken) return
        activePreparation?.cancel()
        activePreparation = coroutineScope.launch {
            var document: M3uDocument? = null
            var accepted = false
            try {
                document = prepare()
                if (!isActive || request.token != requestToken) return@launch

                activePreparation = null
                if (document == null) {
                    onPreparationError()
                } else {
                    fileDocument = document
                    dialog = CreationDialog.FILE
                    accepted = true
                }
            } finally {
                if (!accepted) document?.grant?.let(onReleaseGrant)
            }
        }
    }

    fun cancel() {
        invalidateFilePreparation()
        clearFileDocument()
    }

    private fun clearFileDocument() {
        fileDocument?.grant?.let(onReleaseGrant)
        fileDocument = null
    }

    private fun invalidateFilePreparation() {
        requestToken++
        activePreparation?.cancel()
        activePreparation = null
    }
}

@Composable
fun PlaylistListScreenRoute(
    onPlaylistClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: PlaylistListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val creationCoordinator = remember(coroutineScope, context, viewModel) {
        PlaylistCreationCoordinator(
            coroutineScope = coroutineScope,
            onPreparationError = {
                Toast.makeText(context, R.string.document_permission_error, Toast.LENGTH_LONG).show()
            },
            onReleaseGrant = viewModel::releaseFileGrant,
        )
    }
    var fileRequest by remember { mutableStateOf<M3uFileRequest?>(null) }
    val openDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val request = fileRequest ?: return@rememberLauncherForActivityResult
        fileRequest = null
        uri ?: return@rememberLauncherForActivityResult
        val documentPreparer = M3uDocumentPreparer(
            ioDispatcher = Dispatchers.IO,
            acquirePermission = { viewModel.acquireFileGrant(uri.toString()) },
            releasePermission = { grant -> viewModel.releaseFileGrant(grant) },
            queryDisplayName = { context.contentResolver.queryDisplayName(uri) },
        )
        creationCoordinator.prepareFile(request) {
            documentPreparer.prepare(
                uri = uri.toString(),
                fallbackDisplayName = uri.lastPathSegment.orEmpty(),
            )
        }
    }

    ObservePlaylistEvents(viewModel.events) { event ->
        when (event) {
            is PlaylistListEvent.Created -> onPlaylistClick(event.playlistId)
            is PlaylistListEvent.Message -> Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
            is PlaylistListEvent.FileCreationFailed -> {
                creationCoordinator.dismiss()
                Toast.makeText(context, event.text, Toast.LENGTH_LONG).show()
            }
        }
    }

    PlaylistListScreen(
        uiState = uiState,
        creationCoordinator = creationCoordinator,
        onPickM3uFile = { request ->
            fileRequest = request
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

@Composable
internal fun PlaylistListScreen(
    uiState: PlaylistListUiState,
    creationCoordinator: PlaylistCreationCoordinator? = null,
    onPickM3uFile: (M3uFileRequest) -> Unit = {},
    onPlaylistClick: (Long) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onAction: (PlaylistListAction) -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    val ownedCreationCoordinator = remember(coroutineScope) {
        PlaylistCreationCoordinator(coroutineScope, {}, {})
    }
    val coordinator = creationCoordinator ?: ownedCreationCoordinator
    var playlistToDelete by remember { mutableStateOf<PlaylistSummary?>(null) }
    val context = LocalContext.current
    val isTv = remember { context.isTelevision }
    val showEmptyState = uiState.playlists.isEmpty() && !uiState.isLoading
    val createFocusRequester = remember { FocusRequester() }

    DisposableEffect(coordinator) {
        onDispose(coordinator::cancel)
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
                    coordinator.openChooser()
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

    if (coordinator.dialog == CreationDialog.CHOOSER) {
        CreationChooserDialog(
            onDismissRequest = coordinator::dismiss,
            onCreateEmpty = coordinator::chooseEmpty,
            onCreateUrl = coordinator::chooseUrl,
            onCreateFile = { coordinator.chooseFile(onPickM3uFile) },
        )
    }

    when (coordinator.dialog) {
        CreationDialog.CHOOSER -> Unit
        CreationDialog.EMPTY -> PlaylistNameDialog(
            title = stringResource(R.string.create_empty_playlist),
            isSaving = uiState.isSaving,
            error = uiState.formError,
            onDismissRequest = { dismissCreationDialog(onAction, coordinator::dismiss) },
            onConfirm = { onAction(PlaylistListAction.CreateEditable(it)) },
            onClearError = { onAction(PlaylistListAction.ClearFormError) },
        )
        CreationDialog.URL -> AddM3uUrlDialog(
            isSaving = uiState.isSaving,
            error = uiState.formError,
            onDismissRequest = { dismissCreationDialog(onAction, coordinator::dismiss) },
            onConfirm = { name, url ->
                onAction(PlaylistListAction.CreateLinked(name, PlaylistType.M3U_URL, url))
            },
            onClearError = { onAction(PlaylistListAction.ClearFormError) },
        )
        CreationDialog.FILE -> PlaylistNameDialog(
            title = stringResource(R.string.add_m3u_file_playlist),
            initialName = coordinator.fileDocument?.displayName.orEmpty().withoutM3uExtension(),
            chosenDocument = coordinator.fileDocument?.displayName,
            isSaving = uiState.isSaving,
            error = uiState.formError,
            onDismissRequest = { dismissCreationDialog(onAction, coordinator::dismiss) },
            onConfirm = { name ->
                coordinator.fileDocument?.let { document ->
                    onAction(
                        PlaylistListAction.CreateLinked(
                            name,
                            PlaylistType.M3U_FILE,
                            document.uri,
                            document.grant,
                        ),
                    )
                }
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

internal class M3uDocumentPreparer(
    private val ioDispatcher: CoroutineDispatcher,
    private val acquirePermission: suspend () -> PlaylistFileGrant?,
    private val releasePermission: suspend (PlaylistFileGrant) -> Unit,
    private val queryDisplayName: () -> String?,
) {
    suspend fun prepare(uri: String, fallbackDisplayName: String): M3uDocument? = withContext(ioDispatcher) {
        val grant = acquirePermission() ?: return@withContext null
        try {
            val displayName = try {
                queryDisplayName()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }?.takeIf(String::isNotBlank) ?: fallbackDisplayName
            M3uDocument(uri = uri, displayName = displayName, grant = grant)
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) { releasePermission(grant) }
            throw cancellation
        }
    }
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
