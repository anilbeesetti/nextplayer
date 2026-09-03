package com.graviton.feature.music

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.graviton.core.model.AudioTrack
import com.graviton.core.model.MusicPlaylist
import com.graviton.core.ui.R
import com.graviton.core.ui.designsystem.NextIcons
import com.graviton.feature.music.artwork.MediaArtwork
import com.graviton.feature.music.components.AddToPlaylistDialog
import com.graviton.feature.music.components.MusicEmptyState
import com.graviton.feature.music.components.MusicSectionHeader
import com.graviton.feature.music.components.MusicTile
import com.graviton.feature.music.components.QuickAccessChip
import com.graviton.feature.music.components.TrackActions
import com.graviton.feature.music.components.TrackInformationDialog
import com.graviton.feature.music.components.TrackListItem
import com.graviton.feature.music.components.formatTrackDuration
import com.graviton.feature.music.components.musicItemAppearance
import com.graviton.feature.player.PlayerActivity
import java.util.Calendar

private val musicPermission: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MusicHomeScreen(
    viewModel: MusicViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by viewModel.applicationPreferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionState = rememberPermissionState(musicPermission)
    val connection = rememberMusicSession()
    val controller = connection.controller
    val playback = rememberMusicPlaybackSnapshot(controller)

    var searchOpen by remember { mutableStateOf(false) }
    var sortDialogOpen by remember { mutableStateOf(false) }
    var streamDialogOpen by remember { mutableStateOf(false) }
    var playlistTarget by remember { mutableStateOf<AudioTrack?>(null) }
    var infoTarget by remember { mutableStateOf<AudioTrack?>(null) }

    LaunchedEffect(permissionState.status.isGranted) {
        if (permissionState.status.isGranted) viewModel.refresh()
    }

    BackHandler(enabled = uiState.filter !is MusicFilter.None || searchOpen) {
        if (searchOpen) {
            searchOpen = false
            viewModel.setQuery("")
        } else {
            viewModel.clearFilter()
        }
    }

    val animationsEnabled = preferences.musicAnimationsEnabled

    fun playTrack(track: AudioTrack, queue: List<AudioTrack>) {
        viewModel.recordPlay(track)
        val index = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        controller?.playTracks(queue, index)
    }

    fun actionsFor(track: AudioTrack, queue: List<AudioTrack>) = TrackActions(
        onPlay = {
            if (playback?.mediaId == track.uriString) {
                if (controller?.isPlaying == true) controller.pause() else controller?.play()
            } else {
                playTrack(track, queue)
            }
        },
        onPlayNext = { controller?.playNext(track) },
        onEnqueue = { controller?.enqueue(track) },
        onAddToPlaylist = { playlistTarget = track },
        onToggleFavorite = { viewModel.toggleFavorite(track) },
        onShare = { context.shareTrack(track) },
        onInformation = { infoTarget = track },
    )

    Scaffold(
        topBar = {
            MusicTopBar(
                state = uiState,
                searchOpen = searchOpen,
                onQueryChange = viewModel::setQuery,
                onOpenSearch = {
                    searchOpen = true
                    viewModel.selectSection(MusicSection.TRACKS)
                },
                onCloseSearch = {
                    searchOpen = false
                    viewModel.setQuery("")
                },
                onStreamClick = { streamDialogOpen = true },
                onSortClick = { sortDialogOpen = true },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            MusicSectionChips(
                selected = uiState.section,
                onSelect = viewModel::selectSection,
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    !permissionState.status.isGranted -> MusicEmptyState(
                        icon = NextIcons.Audio,
                        title = stringResource(R.string.music_permission_title),
                        description = stringResource(R.string.music_permission_description),
                        action = {
                            FilledTonalButton(onClick = permissionState::launchPermissionRequest) {
                                Text(stringResource(R.string.allow_access))
                            }
                        },
                    )

                    uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                    uiState.error != null -> MusicEmptyState(
                        icon = NextIcons.Priority,
                        title = stringResource(R.string.music_error_title),
                        description = uiState.error?.message
                            ?: stringResource(R.string.music_error_description),
                        action = {
                            OutlinedButton(onClick = viewModel::refresh) {
                                Text(stringResource(R.string.try_again))
                            }
                        },
                    )

                    else -> MusicLibraryContent(
                        state = uiState,
                        playback = playback,
                        animationsEnabled = animationsEnabled,
                        actionsFor = ::actionsFor,
                        onShuffle = { controller?.playTracks(uiState.tracks.shuffled()) },
                        onPlayTrack = ::playTrack,
                        onResume = {
                            uiState.resumeTrack?.let { track ->
                                playTrack(track, uiState.allTracks)
                                controller?.seekTo(uiState.resumePositionMs)
                            }
                        },
                        onPlayFolder = { path ->
                            val tracks = uiState.allTracks.filter {
                                it.path.substringBeforeLast('/', "") == path
                            }
                            val index = viewModel.folderStartIndex(path, tracks)
                            tracks.getOrNull(index)?.let(viewModel::recordPlay)
                            controller?.playTracks(tracks, index)
                        },
                        onSelectSection = viewModel::selectSection,
                        onSelectPlaylist = viewModel::selectPlaylist,
                        onSelectAlbum = viewModel::selectAlbum,
                        onSelectArtist = viewModel::selectArtist,
                        onSelectFolder = viewModel::selectFolder,
                        onShowFavorites = viewModel::showFavorites,
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

    if (streamDialogOpen) {
        StreamUrlDialog(
            onPlay = { url ->
                streamDialogOpen = false
                context.startActivity(
                    Intent(context, PlayerActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        data = url.toUri()
                    },
                )
            },
            onDismiss = { streamDialogOpen = false },
        )
    }

    playlistTarget?.let { track ->
        val addedMessage = stringResource(R.string.music_playlist_added)
        val failedMessage = stringResource(R.string.music_playlist_add_failed)
        val report: (Boolean) -> Unit = { success ->
            Toast.makeText(
                context,
                if (success) addedMessage else failedMessage,
                Toast.LENGTH_SHORT,
            ).show()
            playlistTarget = null
        }
        AddToPlaylistDialog(
            playlists = uiState.playlists,
            onSelect = { playlist -> viewModel.addTrackToPlaylist(playlist.id, track, report) },
            onCreate = { name -> viewModel.createPlaylistWithTrack(name, track, report) },
            onDismiss = { playlistTarget = null },
        )
    }

    infoTarget?.let { track ->
        TrackInformationDialog(track = track, onDismiss = { infoTarget = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicTopBar(
    state: MusicUiState,
    searchOpen: Boolean,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onStreamClick: () -> Unit,
    onSortClick: () -> Unit,
) {
    TopAppBar(
        title = {
            if (searchOpen) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.search_music)) },
                    singleLine = true,
                    shape = CircleShape,
                )
            } else {
                Column {
                    Text(text = greetingText(), fontWeight = FontWeight.Bold)
                    Text(
                        text = stringResource(R.string.music_library),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            if (searchOpen) {
                IconButton(onClick = onCloseSearch) {
                    Icon(NextIcons.Close, contentDescription = stringResource(R.string.close_search))
                }
            }
        },
        actions = {
            if (!searchOpen) {
                IconButton(onClick = onOpenSearch) {
                    Icon(NextIcons.Search, contentDescription = stringResource(R.string.search_music))
                }
                IconButton(onClick = onStreamClick) {
                    Icon(NextIcons.Link, contentDescription = stringResource(R.string.stream_url))
                }
            }
            IconButton(onClick = onSortClick) {
                Icon(NextIcons.Sort, contentDescription = stringResource(R.string.sort_music))
            }
        },
    )
}

/**
 * Library sections as M3 filter chips.
 *
 * Chips replace the previous tab row so the app-level [androidx.compose.material3.NavigationBar]
 * stays the only tab-like affordance on screen.
 */
@Composable
private fun MusicSectionChips(
    selected: MusicSection,
    onSelect: (MusicSection) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(MusicSection.entries, key = { it.name }) { section ->
            FilterChip(
                selected = section == selected,
                onClick = { onSelect(section) },
                label = { Text(stringResource(section.labelRes)) },
            )
        }
    }
}

@Composable
private fun MusicLibraryContent(
    state: MusicUiState,
    playback: MusicPlaybackSnapshot?,
    animationsEnabled: Boolean,
    actionsFor: (AudioTrack, List<AudioTrack>) -> TrackActions,
    onShuffle: () -> Unit,
    onPlayTrack: (AudioTrack, List<AudioTrack>) -> Unit,
    onResume: () -> Unit,
    onPlayFolder: (String) -> Unit,
    onSelectSection: (MusicSection) -> Unit,
    onSelectPlaylist: (MusicPlaylist) -> Unit,
    onSelectAlbum: (String) -> Unit,
    onSelectArtist: (String) -> Unit,
    onSelectFolder: (String) -> Unit,
    onShowFavorites: () -> Unit,
    onClearFilter: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // The bottom inset leaves room for the mini bar sitting above the navigation bar.
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        state.activeFilterLabel()?.let { label ->
            item(key = "filter") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = onClearFilter,
                        label = { Text(label) },
                        trailingIcon = {
                            Icon(
                                imageVector = NextIcons.Close,
                                contentDescription = stringResource(R.string.clear_filter),
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
        }

        when (state.section) {
            MusicSection.HOME -> homeDashboard(
                state = state,
                playback = playback,
                animationsEnabled = animationsEnabled,
                actionsFor = actionsFor,
                onShuffle = onShuffle,
                onPlayTrack = onPlayTrack,
                onResume = onResume,
                onSelectSection = onSelectSection,
                onSelectAlbum = onSelectAlbum,
                onSelectArtist = onSelectArtist,
                onShowFavorites = onShowFavorites,
            )

            MusicSection.TRACKS -> trackList(
                tracks = state.tracks,
                state = state,
                playback = playback,
                animationsEnabled = animationsEnabled,
                actionsFor = actionsFor,
                onShuffle = onShuffle,
            )

            MusicSection.PLAYLISTS -> {
                if (state.playlists.isEmpty()) {
                    item {
                        MusicEmptyState(
                            icon = NextIcons.Playlist,
                            title = stringResource(R.string.no_music_playlists_title),
                            description = stringResource(R.string.no_music_playlists_description),
                        )
                    }
                } else {
                    items(state.playlists, key = { "playlist-${it.id}" }) { playlist ->
                        CollectionListItem(
                            title = playlist.name.ifBlank { stringResource(R.string.untitled_playlist) },
                            subtitle = pluralStringResource(
                                R.plurals.song_count,
                                playlist.trackCount,
                                playlist.trackCount,
                            ),
                            artworkUri = null,
                            mediaUri = null,
                            icon = NextIcons.Playlist,
                            onClick = { onSelectPlaylist(playlist) },
                        )
                    }
                }
            }

            MusicSection.ALBUMS -> {
                val albums = state.albums
                if (albums.isEmpty()) {
                    item {
                        MusicEmptyState(
                            icon = NextIcons.Album,
                            title = stringResource(R.string.no_albums_title),
                            description = stringResource(R.string.no_albums_description),
                        )
                    }
                } else {
                    items(albums, key = { "album-${it.name}" }) { album ->
                        CollectionListItem(
                            title = album.name,
                            subtitle = pluralStringResource(
                                R.plurals.song_count,
                                album.trackCount,
                                album.trackCount,
                            ),
                            artworkUri = album.artworkUri,
                            mediaUri = album.mediaUri,
                            icon = NextIcons.Album,
                            onClick = { onSelectAlbum(album.name) },
                        )
                    }
                }
            }

            MusicSection.ARTISTS -> {
                val artists = state.artists
                if (artists.isEmpty()) {
                    item {
                        MusicEmptyState(
                            icon = NextIcons.Artist,
                            title = stringResource(R.string.no_artists_title),
                            description = stringResource(R.string.no_artists_description),
                        )
                    }
                } else {
                    items(artists, key = { "artist-${it.name}" }) { artist ->
                        CollectionListItem(
                            title = artist.name,
                            subtitle = pluralStringResource(
                                R.plurals.song_count,
                                artist.trackCount,
                                artist.trackCount,
                            ),
                            artworkUri = artist.artworkUri,
                            mediaUri = artist.mediaUri,
                            icon = NextIcons.Artist,
                            onClick = { onSelectArtist(artist.name) },
                        )
                    }
                }
            }

            MusicSection.FOLDERS -> {
                val folders = state.folders
                if (folders.isEmpty()) {
                    item {
                        MusicEmptyState(
                            icon = NextIcons.Folder,
                            title = stringResource(R.string.no_folders_title),
                            description = stringResource(R.string.no_folders_description),
                        )
                    }
                } else {
                    items(folders, key = { "folder-${it.name}" }) { folder ->
                        CollectionListItem(
                            title = folder.name.substringAfterLast('/').ifBlank { folder.name },
                            subtitle = pluralStringResource(
                                R.plurals.song_count,
                                folder.trackCount,
                                folder.trackCount,
                            ),
                            artworkUri = folder.artworkUri,
                            mediaUri = folder.mediaUri,
                            icon = NextIcons.Folder,
                            onClick = { onSelectFolder(folder.name) },
                            onPlay = { onPlayFolder(folder.name) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The Home dashboard.
 *
 * Every section is backed by data the app already records — the resume point comes from the saved
 * queue, "Most played" from the persisted play counts — so nothing here is placeholder content.
 */
private fun LazyListScope.homeDashboard(
    state: MusicUiState,
    playback: MusicPlaybackSnapshot?,
    animationsEnabled: Boolean,
    actionsFor: (AudioTrack, List<AudioTrack>) -> TrackActions,
    onShuffle: () -> Unit,
    onPlayTrack: (AudioTrack, List<AudioTrack>) -> Unit,
    onResume: () -> Unit,
    onSelectSection: (MusicSection) -> Unit,
    onSelectAlbum: (String) -> Unit,
    onSelectArtist: (String) -> Unit,
    onShowFavorites: () -> Unit,
) {
    if (state.allTracks.isEmpty()) {
        item {
            MusicEmptyState(
                icon = NextIcons.Audio,
                title = stringResource(R.string.no_music_title),
                description = stringResource(R.string.no_music_description),
            )
        }
        return
    }

    item(key = "quick-access") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MusicSectionHeader(title = stringResource(R.string.music_quick_access))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAccessChip(
                    icon = NextIcons.Favorite,
                    label = stringResource(R.string.favorites),
                    onClick = onShowFavorites,
                )
                QuickAccessChip(
                    icon = NextIcons.Playlist,
                    label = stringResource(R.string.playlists),
                    onClick = { onSelectSection(MusicSection.PLAYLISTS) },
                )
                QuickAccessChip(
                    icon = NextIcons.Shuffle,
                    label = stringResource(R.string.shuffle_all),
                    onClick = onShuffle,
                )
            }
        }
    }

    state.resumeTrack?.let { track ->
        item(key = "resume") {
            Column {
                MusicSectionHeader(title = stringResource(R.string.continue_listening))
                ListItem(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    headlineContent = {
                        Text(track.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(
                            text = "${track.displayArtist} • ${formatTrackDuration(state.resumePositionMs)}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        FilledTonalButton(onClick = onResume) {
                            Text(stringResource(R.string.resume_playback))
                        }
                    },
                )
            }
        }
    }

    if (state.favorites.isNotEmpty()) {
        carouselSection(
            key = "favorites",
            titleRes = R.string.favorites,
            tracks = state.favorites,
            onShowAll = onShowFavorites,
            onPlayTrack = { onPlayTrack(it, state.favorites) },
        )
    }

    if (state.recentlyPlayed.isNotEmpty()) {
        carouselSection(
            key = "recent",
            titleRes = R.string.recently_played,
            tracks = state.recentlyPlayed,
            onShowAll = null,
            onPlayTrack = { onPlayTrack(it, state.recentlyPlayed) },
        )
    }

    if (state.recentlyAdded.isNotEmpty()) {
        carouselSection(
            key = "added",
            titleRes = R.string.recently_added,
            tracks = state.recentlyAdded,
            onShowAll = null,
            onPlayTrack = { onPlayTrack(it, state.recentlyAdded) },
        )
    }

    if (state.mostPlayed.isNotEmpty()) {
        item(key = "most-played-header") {
            MusicSectionHeader(title = stringResource(R.string.most_played))
        }
        itemsIndexed(
            items = state.mostPlayed,
            key = { _, track -> "most-${track.uriString}" },
        ) { index, track ->
            TrackListItem(
                track = track,
                active = playback?.mediaId == track.uriString,
                playing = playback?.let { it.mediaId == track.uriString && it.isPlaying } == true,
                isFavorite = track.uriString in state.favoriteUris,
                actions = actionsFor(track, state.mostPlayed),
                modifier = Modifier.musicItemAppearance(animationsEnabled, index),
            )
        }
    }

    if (state.albums.isNotEmpty()) {
        item(key = "albums-row") {
            Column {
                MusicSectionHeader(
                    title = stringResource(R.string.albums),
                    onShowAll = { onSelectSection(MusicSection.ALBUMS) },
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.albums.take(HOME_ROW_LIMIT), key = { it.name }) { album ->
                        MusicTile(
                            title = album.name,
                            subtitle = pluralStringResource(
                                R.plurals.song_count,
                                album.trackCount,
                                album.trackCount,
                            ),
                            artworkUri = album.artworkUri,
                            mediaUri = album.mediaUri,
                            fallback = NextIcons.Album,
                            onClick = { onSelectAlbum(album.name) },
                        )
                    }
                }
            }
        }
    }

    if (state.artists.isNotEmpty()) {
        item(key = "artists-row") {
            Column {
                MusicSectionHeader(
                    title = stringResource(R.string.artists),
                    onShowAll = { onSelectSection(MusicSection.ARTISTS) },
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.artists.take(HOME_ROW_LIMIT), key = { it.name }) { artist ->
                        MusicTile(
                            title = artist.name,
                            subtitle = pluralStringResource(
                                R.plurals.song_count,
                                artist.trackCount,
                                artist.trackCount,
                            ),
                            artworkUri = artist.artworkUri,
                            mediaUri = artist.mediaUri,
                            fallback = NextIcons.Artist,
                            onClick = { onSelectArtist(artist.name) },
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.carouselSection(
    key: String,
    @StringRes titleRes: Int,
    tracks: List<AudioTrack>,
    onShowAll: (() -> Unit)?,
    onPlayTrack: (AudioTrack) -> Unit,
) {
    item(key = "$key-row") {
        Column {
            MusicSectionHeader(title = stringResource(titleRes), onShowAll = onShowAll)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(tracks.take(HOME_ROW_LIMIT), key = { "$key-${it.uriString}" }) { track ->
                    MusicTile(
                        title = track.displayTitle,
                        subtitle = track.displayArtist,
                        artworkUri = track.artworkUriString,
                        mediaUri = track.uriString,
                        onClick = { onPlayTrack(track) },
                    )
                }
            }
        }
    }
}

private fun LazyListScope.trackList(
    tracks: List<AudioTrack>,
    state: MusicUiState,
    playback: MusicPlaybackSnapshot?,
    animationsEnabled: Boolean,
    actionsFor: (AudioTrack, List<AudioTrack>) -> TrackActions,
    onShuffle: () -> Unit,
) {
    if (tracks.isEmpty()) {
        item {
            MusicEmptyState(
                icon = NextIcons.Audio,
                title = stringResource(R.string.no_music_title),
                description = stringResource(R.string.no_music_description),
            )
        }
        return
    }

    item(key = "tracks-header") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pluralStringResource(R.plurals.song_count, tracks.size, tracks.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = onShuffle) {
                Icon(NextIcons.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.shuffle_all))
            }
        }
    }

    itemsIndexed(tracks, key = { _, track -> "track-${track.uriString}" }) { index, track ->
        TrackListItem(
            track = track,
            active = playback?.mediaId == track.uriString,
            playing = playback?.let { it.mediaId == track.uriString && it.isPlaying } == true,
            isFavorite = track.uriString in state.favoriteUris,
            actions = actionsFor(track, tracks),
            modifier = Modifier.musicItemAppearance(animationsEnabled, index),
        )
    }
}

@Composable
private fun CollectionListItem(
    title: String,
    subtitle: String,
    artworkUri: String?,
    mediaUri: String?,
    icon: ImageVector,
    onClick: () -> Unit,
    onPlay: (() -> Unit)? = null,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            MediaArtwork(
                artworkUri = artworkUri,
                mediaUri = mediaUri,
                modifier = Modifier.size(52.dp),
                fallback = icon,
            )
        },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onPlay != null) {
                    IconButton(onClick = onPlay) {
                        Icon(NextIcons.Play, contentDescription = stringResource(R.string.play_folder))
                    }
                }
                Icon(
                    imageVector = NextIcons.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    )
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
        icon = { Icon(NextIcons.Sort, contentDescription = null) },
        title = { Text(stringResource(R.string.sort_music)) },
        text = {
            Column {
                MusicSort.entries.forEach { sort ->
                    ListItem(
                        modifier = Modifier.clickable { onSelected(sort) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(stringResource(sort.labelRes)) },
                        trailingContent = if (sort == selected) {
                            { Icon(NextIcons.Check, contentDescription = null) }
                        } else {
                            null
                        },
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                TextButton(onClick = onToggleDirection) {
                    Text(
                        stringResource(
                            if (ascending) R.string.sort_descending else R.string.sort_ascending,
                        ),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
private fun StreamUrlDialog(onPlay: (String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(NextIcons.Link, contentDescription = null) },
        title = { Text(stringResource(R.string.stream_url)) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.example_url)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = url.isNotBlank(), onClick = { onPlay(url.trim()) }) {
                Text(stringResource(R.string.play))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Shares the song's own content URI through the system chooser. */
private fun Context.shareTrack(track: AudioTrack) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/*"
        putExtra(Intent.EXTRA_STREAM, track.uriString.toUri())
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { startActivity(Intent.createChooser(intent, null)) }
}

@Composable
private fun greetingText(): String {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    return stringResource(
        when (hour) {
            in 5..11 -> R.string.good_morning
            in 12..17 -> R.string.good_afternoon
            else -> R.string.good_evening
        },
    )
}

/** How many entries each Home carousel shows before "Show all". */
private const val HOME_ROW_LIMIT = 12
