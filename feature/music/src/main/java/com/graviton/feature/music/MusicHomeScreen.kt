package com.graviton.feature.music

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.graviton.core.model.AudioTrack
import com.graviton.core.model.MusicPlaylist
import com.graviton.core.ui.designsystem.NextIcons
import kotlinx.coroutines.delay

private val musicPermission: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MusicHomeScreen(
    viewModel: MusicViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionState = rememberPermissionState(musicPermission)
    val connection = rememberMusicSession()
    val controller = connection.controller
    val playback = rememberMusicPlaybackSnapshot(controller)
    var searchOpen by remember { mutableStateOf(false) }
    var sortDialogOpen by remember { mutableStateOf(false) }

    LaunchedEffect(permissionState.status.isGranted) {
        if (permissionState.status.isGranted) viewModel.refresh()
    }
    LaunchedEffect(playback) {
        while (playback != null) {
            playback.refresh()
            delay(500)
        }
    }

    val selectedTab = MusicSection.entries.indexOf(uiState.section).coerceAtLeast(0)
    BackHandler(enabled = uiState.filter !is MusicFilter.None) {
        viewModel.clearFilter()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchOpen) {
                        OutlinedTextField(
                            value = uiState.query,
                            onValueChange = viewModel::setQuery,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search songs") },
                            singleLine = true,
                            shape = CircleShape,
                        )
                    } else {
                        Column {
                            Text("Music", fontWeight = FontWeight.Bold)
                            (uiState.filter as? MusicFilter.Playlist)?.let {
                                Text(
                                    text = it.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (searchOpen) {
                        IconButton(onClick = { searchOpen = false; viewModel.setQuery("") }) {
                            Icon(NextIcons.Close, contentDescription = "Close search")
                        }
                    }
                },
                actions = {
                    if (!searchOpen) {
                        IconButton(onClick = { searchOpen = true; viewModel.selectSection(MusicSection.TRACKS) }) {
                            Icon(NextIcons.Search, contentDescription = "Search songs")
                        }
                    }
                    IconButton(onClick = { sortDialogOpen = true }) {
                        Icon(NextIcons.Reorder, contentDescription = "Sort music")
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                divider = {},
            ) {
                MusicSection.entries.forEachIndexed { index, section ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { viewModel.selectSection(section) },
                        text = { Text(section.label) },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MaterialTheme.colorScheme.background),
            ) {
                when {
                    !permissionState.status.isGranted -> MusicPermissionContent(permissionState::launchPermissionRequest)
                    uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    uiState.error != null -> MusicErrorContent(uiState.error, viewModel::refresh)
                    else -> MusicLibraryContent(
                        state = uiState,
                        playback = playback,
                        onShuffle = {
                            controller?.playTracks(uiState.tracks.shuffled())
                        },
                        onPlayTrack = { track ->
                            val queue = if (uiState.tracks.isNotEmpty()) uiState.tracks else uiState.allTracks
                            val index = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                            controller?.playTracks(queue, index)
                        },
                        onPause = { if (controller?.isPlaying == true) controller.pause() else controller?.play() },
                        onPlayNext = { controller?.playNext(it) },
                        onEnqueue = { controller?.enqueue(it) },
                        onOpenFullPlayer = {
                            val current = uiState.allTracks.firstOrNull { it.uriString == playback?.mediaId }
                            if (current != null) context.openMusicPlayer(current, uiState.allTracks)
                        },
                        onSelectPlaylist = viewModel::selectPlaylist,
                        onSelectAlbum = viewModel::selectAlbum,
                        onSelectArtist = viewModel::selectArtist,
                        onSelectFolder = viewModel::selectFolder,
                        onClearFilter = viewModel::clearFilter,
                    )
                }
            }
        }
    }

    if (sortDialogOpen) {
        MusicSortDialog(
            selected = uiState.sort,
            ascending = uiState.ascending,
            onSelected = { viewModel.setSort(it); sortDialogOpen = false },
            onToggleDirection = viewModel::toggleSortDirection,
            onDismiss = { sortDialogOpen = false },
        )
    }
}

@Composable
private fun MusicPermissionContent(request: () -> Unit) {
    EmptyMusicState(
        icon = NextIcons.Audio,
        title = "Music permission needed",
        body = "Allow Graviton to read audio on this device to build your library.",
        action = { FilledTonalButton(onClick = request) { Text("Allow access") } },
    )
}

@Composable
private fun MusicErrorContent(error: Throwable?, retry: () -> Unit) {
    EmptyMusicState(
        icon = NextIcons.Priority,
        title = "Music could not be loaded",
        body = error?.message ?: "MediaStore did not return the music library.",
        action = { OutlinedButton(onClick = retry) { Text("Try again") } },
    )
}

@Composable
private fun MusicLibraryContent(
    state: MusicUiState,
    playback: MusicPlaybackSnapshot?,
    onShuffle: () -> Unit,
    onPlayTrack: (AudioTrack) -> Unit,
    onPause: () -> Unit,
    onPlayNext: (AudioTrack) -> Unit,
    onEnqueue: (AudioTrack) -> Unit,
    onOpenFullPlayer: () -> Unit,
    onSelectPlaylist: (MusicPlaylist) -> Unit,
    onSelectAlbum: (String) -> Unit,
    onSelectArtist: (String) -> Unit,
    onSelectFolder: (String) -> Unit,
    onClearFilter: () -> Unit,
) {
    val displayTracks = state.tracks
    val hasTracks = state.allTracks.isNotEmpty()
    val filterLabel = when (val filter = state.filter) {
        MusicFilter.None -> null
        is MusicFilter.Album -> "Album · ${filter.name}"
        is MusicFilter.Artist -> "Artist · ${filter.name}"
        is MusicFilter.Folder -> "Folder · ${filter.path.substringAfterLast('/')}"
        is MusicFilter.Playlist -> "Playlist · ${filter.name}"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "heading") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (state.section) {
                            MusicSection.TRACKS -> filterLabel ?: "All songs"
                            else -> state.section.label
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (state.section == MusicSection.TRACKS) {
                            "${displayTracks.size} songs"
                        } else {
                            sectionCount(state)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.section == MusicSection.TRACKS && displayTracks.isNotEmpty()) {
                    FilledTonalButton(onClick = onShuffle) {
                        Icon(NextIcons.Shuffle, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle all")
                    }
                }
            }
            filterLabel?.let {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.clickable(onClick = onClearFilter),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(it, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(6.dp))
                        Icon(NextIcons.Close, contentDescription = "Clear filter", Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        when (state.section) {
            MusicSection.TRACKS -> {
                if (displayTracks.isEmpty()) {
                    item(key = "empty-tracks") {
                        EmptyMusicState(
                            icon = NextIcons.Audio,
                            title = if (hasTracks) "No matching songs" else "No songs found",
                            body = if (hasTracks) "Try another search or clear the filter." else "Add music to this device and refresh the library.",
                            action = null,
                        )
                    }
                } else {
                    itemsIndexed(displayTracks, key = { _, track -> track.uriString }) { _, track ->
                        TrackRow(
                            track = track,
                            active = playback?.mediaId == track.uriString,
                            playing = playback?.let { it.mediaId == track.uriString && it.isPlaying } == true,
                            onClick = { onPlayTrack(track) },
                            onPause = onPause,
                            onPlayNext = { onPlayNext(track) },
                            onEnqueue = { onEnqueue(track) },
                        )
                    }
                }
            }
            MusicSection.PLAYLISTS -> {
                if (state.playlists.isEmpty()) {
                    item { EmptyMusicState(NextIcons.Playlist, "No playlists found", "Playlists created by other music apps appear here.", null) }
                } else {
                    items(state.playlists, key = { it.id }) { playlist ->
                        CollectionRow(
                            title = playlist.name.ifBlank { "Untitled playlist" },
                            subtitle = "${playlist.trackCount} songs",
                            artworkUri = null,
                            icon = NextIcons.Playlist,
                            onClick = { onSelectPlaylist(playlist) },
                        )
                    }
                }
            }
            MusicSection.ALBUMS -> {
                val albums = state.allTracks.groupBy { it.displayAlbum }.toList().sortedBy { it.first.lowercase() }
                if (albums.isEmpty()) item { EmptyMusicState(NextIcons.Image, "No albums found", "Album information will appear with your music.", null) }
                else items(albums, key = { it.first }) { (album, tracks) ->
                    CollectionRow(album, "${tracks.size} songs · ${tracks.first().displayArtist}", tracks.first().artworkUriString, NextIcons.Image) { onSelectAlbum(album) }
                }
            }
            MusicSection.ARTISTS -> {
                val artists = state.allTracks.groupBy { it.displayArtist }.toList().sortedBy { it.first.lowercase() }
                if (artists.isEmpty()) item { EmptyMusicState(NextIcons.Audio, "No artists found", "Artist information will appear with your music.", null) }
                else items(artists, key = { it.first }) { (artist, tracks) ->
                    CollectionRow(artist, "${tracks.size} songs", tracks.first().artworkUriString, NextIcons.Audio) { onSelectArtist(artist) }
                }
            }
            MusicSection.FOLDERS -> {
                val folders = state.allTracks.groupBy { it.path.substringBeforeLast('/', "") }.toList().sortedBy { it.first.lowercase() }
                if (folders.isEmpty()) item { EmptyMusicState(NextIcons.Folder, "No folders found", "Music folders will appear here.", null) }
                else items(folders, key = { it.first }) { (folder, tracks) ->
                    CollectionRow(folder.substringAfterLast('/').ifBlank { "Music" }, "${tracks.size} songs", tracks.first().artworkUriString, NextIcons.Folder) { onSelectFolder(folder) }
                }
            }
        }

        item(key = "mini-player-spacer") { Spacer(Modifier.height(8.dp)) }
    }

    playback?.let { snapshot ->
        if (snapshot.mediaId != null && snapshot.isMusic) {
            Box(modifier = Modifier.fillMaxSize()) {
                MiniPlayer(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    snapshot = snapshot,
                    onTogglePlay = onPause,
                    onOpen = onOpenFullPlayer,
                )
            }
        }
    }
}

private fun sectionCount(state: MusicUiState): String = when (state.section) {
    MusicSection.PLAYLISTS -> "${state.playlists.size} playlists"
    MusicSection.ALBUMS -> "${state.allTracks.map { it.displayAlbum }.distinct().size} albums"
    MusicSection.ARTISTS -> "${state.allTracks.map { it.displayArtist }.distinct().size} artists"
    MusicSection.FOLDERS -> "${state.allTracks.map { it.path.substringBeforeLast('/', "") }.distinct().size} folders"
    MusicSection.TRACKS -> "${state.tracks.size} songs"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    track: AudioTrack,
    active: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    onPause: () -> Unit,
    onPlayNext: () -> Unit,
    onEnqueue: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true }),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(track.artworkUriString, NextIcons.Audio, Modifier.size(56.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(track.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(
                    text = "${track.displayArtist} · ${track.displayAlbum}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatDuration(track.duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(NextIcons.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (active) {
                        DropdownMenuItem(
                            text = { Text(if (playing) "Pause" else "Resume") },
                            onClick = { menuOpen = false; onPause() },
                            leadingIcon = { Icon(if (playing) NextIcons.Pause else NextIcons.Play, null) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Play next") },
                        onClick = { menuOpen = false; onPlayNext() },
                        leadingIcon = { Icon(NextIcons.PlaylistAdd, null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Add to queue") },
                        onClick = { menuOpen = false; onEnqueue() },
                        leadingIcon = { Icon(NextIcons.QueueMusic, null) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionRow(
    title: String,
    subtitle: String,
    artworkUri: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(artworkUri, icon, Modifier.size(58.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(NextIcons.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp).rotate(180f))
        }
    }
}

@Composable
private fun MiniPlayer(
    modifier: Modifier = Modifier,
    snapshot: MusicPlaybackSnapshot,
    onTogglePlay: () -> Unit,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 4.dp,
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(snapshot.artworkUri?.toString(), NextIcons.Audio, Modifier.size(48.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(snapshot.title.ifBlank { "Playing" }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(snapshot.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onTogglePlay) {
                Icon(if (snapshot.isPlaying) NextIcons.Pause else NextIcons.Play, contentDescription = if (snapshot.isPlaying) "Pause" else "Play")
            }
        }
    }
}

@Composable
private fun Artwork(uri: String?, fallback: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(fallback, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
        if (!uri.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(uri).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun EmptyMusicState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    action: (@Composable () -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(18.dp).size(32.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}

@Composable
private fun MusicSortDialog(
    selected: MusicSort,
    ascending: Boolean,
    onSelected: (MusicSort) -> Unit,
    onToggleDirection: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort music") },
        text = {
            Column {
                MusicSort.entries.forEach { sort ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelected(sort) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(sort.label, Modifier.weight(1f))
                        if (sort == selected) Text(if (ascending) "A–Z" else "Z–A", color = MaterialTheme.colorScheme.primary)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                TextButton(onClick = onToggleDirection) { Text(if (ascending) "Descending" else "Ascending") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
