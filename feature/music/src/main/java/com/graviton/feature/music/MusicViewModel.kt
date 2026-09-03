package com.graviton.feature.music

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.graviton.core.data.repository.MusicRepository
import com.graviton.core.data.repository.PreferencesRepository
import com.graviton.core.model.AudioTrack
import com.graviton.core.model.MusicPlaylist
import com.graviton.core.model.lastMusicUriForFolder
import com.graviton.core.model.recordMusicPlay
import com.graviton.core.model.startIndexForFolderPlayback
import com.graviton.core.model.toggleMusicFavorite
import com.graviton.core.ui.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Sections shown in the music library. The names also describe the actual MediaStore query used. */
enum class MusicSection(@StringRes val labelRes: Int) {
    HOME(R.string.home),
    TRACKS(R.string.tracks),
    PLAYLISTS(R.string.playlists),
    ALBUMS(R.string.albums),
    ARTISTS(R.string.artists),
    FOLDERS(R.string.folders),
}

enum class MusicSort(@StringRes val labelRes: Int) {
    TITLE(R.string.title),
    ARTIST(R.string.artist),
    ALBUM(R.string.album),
    DATE_ADDED(R.string.recently_added),
    DURATION(R.string.duration),
}

sealed interface MusicFilter {
    data object None : MusicFilter
    data class Album(val name: String) : MusicFilter
    data class Artist(val name: String) : MusicFilter
    data class Folder(val path: String) : MusicFilter
    data class Playlist(val id: Long, val name: String, val trackIds: Set<Long> = emptySet()) : MusicFilter
    data object Favorites : MusicFilter
}

/**
 * A grouping of tracks (album, artist or folder) precomputed in the ViewModel.
 *
 * Grouping is done once per library change rather than inside the composable, so scrolling never
 * re-runs the grouping pass.
 */
@androidx.compose.runtime.Immutable
data class MusicCollection(
    val name: String,
    val trackCount: Int,
    val artworkUri: String?,
    val mediaUri: String?,
)

@androidx.compose.runtime.Stable
data class MusicUiState(
    val isLoading: Boolean = true,
    val error: Throwable? = null,
    val allTracks: List<AudioTrack> = emptyList(),
    val tracks: List<AudioTrack> = emptyList(),
    val playlists: List<MusicPlaylist> = emptyList(),
    val section: MusicSection = MusicSection.HOME,
    val recentlyPlayed: List<AudioTrack> = emptyList(),
    val recentlyAdded: List<AudioTrack> = emptyList(),
    val mostPlayed: List<AudioTrack> = emptyList(),
    val favorites: List<AudioTrack> = emptyList(),
    /** URIs of favourited tracks, for O(1) lookup while rendering rows. */
    val favoriteUris: Set<String> = emptySet(),
    /**
     * The saved queue position, if playback was interrupted part-way through a track. This is the
     * real resume point persisted by the player service, not a re-listing of the play history.
     */
    val resumeTrack: AudioTrack? = null,
    val resumePositionMs: Long = 0L,
    val query: String = "",
    val sort: MusicSort = MusicSort.TITLE,
    val ascending: Boolean = true,
    val filter: MusicFilter = MusicFilter.None,
    val isFilterLoading: Boolean = false,
    val albums: List<MusicCollection> = emptyList(),
    val artists: List<MusicCollection> = emptyList(),
    val folders: List<MusicCollection> = emptyList(),
) {
    /** A human label for the active filter, or `null` when the whole library is shown. */
    @Composable
    fun activeFilterLabel(): String? = when (val current = filter) {
        MusicFilter.None -> null
        MusicFilter.Favorites -> stringResource(R.string.favorites)
        is MusicFilter.Album -> "${stringResource(R.string.album)} • ${current.name}"
        is MusicFilter.Artist -> "${stringResource(R.string.artist)} • ${current.name}"
        is MusicFilter.Folder -> "${stringResource(R.string.folder)} • ${current.path.substringAfterLast('/')}"
        is MusicFilter.Playlist -> "${stringResource(R.string.playlist)} • ${current.name}"
    }
}

/** Below this, a saved position is treated as "not started" rather than something to resume. */
private const val RESUME_THRESHOLD_MS = 5_000L

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {
    private val stateInternal = MutableStateFlow(MusicUiState())
    val uiState = stateInternal.asStateFlow()

    /** The shared app preferences, reused rather than duplicated into this screen's state. */
    val applicationPreferences = preferencesRepository.applicationPreferences

    private val sourceTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    private val sourcePlaylists = MutableStateFlow<List<MusicPlaylist>>(emptyList())
    private var tracksLoaded = false
    private var filterJob: Job? = null

    init {
        viewModelScope.launch {
            combine(sourceTracks, sourcePlaylists) { tracks, playlists -> tracks to playlists }
                .catch { error -> stateInternal.update { it.copy(isLoading = false, error = error) } }
                .collect { (tracks, playlists) ->
                    stateInternal.update {
                        it.copy(
                            isLoading = !tracksLoaded,
                            error = null,
                            allTracks = tracks,
                            playlists = playlists,
                        )
                    }
                    recomputeVisibleTracks()
                }
        }
        viewModelScope.launch {
            musicRepository.observeTracks()
                .catch { error -> stateInternal.update { it.copy(isLoading = false, error = error) } }
                .collect {
                    tracksLoaded = true
                    sourceTracks.value = it
                }
        }
        viewModelScope.launch {
            musicRepository.observePlaylists()
                .catch { error -> stateInternal.update { it.copy(isLoading = false, error = error) } }
                .collect { sourcePlaylists.value = it }
        }
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { prefs ->
                val tracks = sourceTracks.value
                val recent = prefs.musicRecentlyPlayedUris.mapNotNull { uri ->
                    tracks.firstOrNull { it.uriString == uri }
                }
                stateInternal.update { it.copy(recentlyPlayed = recent) }
                recomputeVisibleTracks()
            }
        }
    }

    fun refresh() {
        tracksLoaded = false
        stateInternal.update { it.copy(isLoading = true, error = null) }
        musicRepository.refresh()
    }

    fun selectSection(section: MusicSection) {
        stateInternal.update { it.copy(section = section, filter = MusicFilter.None, isFilterLoading = false) }
        recomputeVisibleTracks()
    }

    fun setQuery(query: String) {
        stateInternal.update { it.copy(query = query) }
        recomputeVisibleTracks()
    }

    fun setSort(sort: MusicSort) {
        stateInternal.update { it.copy(sort = sort) }
        recomputeVisibleTracks()
    }

    fun toggleSortDirection() {
        stateInternal.update { it.copy(ascending = !it.ascending) }
        recomputeVisibleTracks()
    }

    /** Switches to the Tracks list filtered to favourites. */
    fun showFavorites() {
        stateInternal.update { it.copy(section = MusicSection.TRACKS, filter = MusicFilter.Favorites) }
        recomputeVisibleTracks()
    }

    fun clearFilter() {
        stateInternal.update { it.copy(filter = MusicFilter.None) }
        recomputeVisibleTracks()
    }

    fun selectAlbum(album: String) {
        stateInternal.update { it.copy(section = MusicSection.TRACKS, filter = MusicFilter.Album(album)) }
        recomputeVisibleTracks()
    }

    fun selectArtist(artist: String) {
        stateInternal.update { it.copy(section = MusicSection.TRACKS, filter = MusicFilter.Artist(artist)) }
        recomputeVisibleTracks()
    }

    fun selectFolder(path: String) {
        stateInternal.update { it.copy(section = MusicSection.TRACKS, filter = MusicFilter.Folder(path)) }
        recomputeVisibleTracks()
    }

    fun folderStartIndex(path: String, tracks: List<AudioTrack>): Int {
        val last = preferencesRepository.applicationPreferences.value.lastMusicUriForFolder(path)
        return startIndexForFolderPlayback(tracks.map { it.uriString }, last)
    }

    /**
     * Appends [track] to the MediaStore playlist [playlistId].
     *
     * [onResult] receives `false` when the platform refuses the write, so the UI can say so instead
     * of silently doing nothing.
     */
    fun addTrackToPlaylist(playlistId: Long, track: AudioTrack, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val added = runCatching { musicRepository.addTracksToPlaylist(playlistId, listOf(track.id)) }
                .getOrDefault(false)
            onResult(added)
        }
    }

    /** Creates a playlist and immediately puts [track] in it. */
    fun createPlaylistWithTrack(name: String, track: AudioTrack, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val id = runCatching { musicRepository.createPlaylist(name) }.getOrNull()
            val added = id != null &&
                runCatching { musicRepository.addTracksToPlaylist(id, listOf(track.id)) }.getOrDefault(false)
            onResult(added)
        }
    }

    /** Toggles the favourite flag for [track] and persists it. */
    fun toggleFavorite(track: AudioTrack) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences { it.toggleMusicFavorite(track.uriString) }
            recomputeVisibleTracks()
        }
    }

    fun recordPlay(track: AudioTrack) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                // countPlay is what feeds musicPlayCounts, which the "Most played" section reads.
                it.recordMusicPlay(
                    uri = track.uriString,
                    folderPath = track.path.substringBeforeLast('/', ""),
                    countPlay = true,
                )
            }
            recomputeVisibleTracks()
        }
    }

    fun selectPlaylist(playlist: MusicPlaylist) {
        filterJob?.cancel()
        stateInternal.update {
            it.copy(
                section = MusicSection.TRACKS,
                filter = MusicFilter.Playlist(playlist.id, playlist.name),
                isFilterLoading = true,
            )
        }
        filterJob = viewModelScope.launch {
            val ids = runCatching { musicRepository.getPlaylistTrackIds(playlist.id).toSet() }.getOrDefault(emptySet())
            stateInternal.update { current ->
                current.copy(filter = MusicFilter.Playlist(playlist.id, playlist.name, ids), isFilterLoading = false)
            }
            recomputeVisibleTracks()
        }
    }

    private fun recomputeVisibleTracks() {
        val current = stateInternal.value
        val filtered = current.allTracks
            .asSequence()
            .filter { track ->
                when (val filter = current.filter) {
                    MusicFilter.None -> true
                    MusicFilter.Favorites -> track.uriString in preferencesRepository
                        .applicationPreferences.value.musicFavorites
                    is MusicFilter.Album -> track.displayAlbum == filter.name
                    is MusicFilter.Artist -> track.displayArtist == filter.name
                    is MusicFilter.Folder -> track.path.substringBeforeLast('/', "") == filter.path
                    is MusicFilter.Playlist -> filter.trackIds.contains(track.id)
                }
            }
            .filter { track ->
                val query = current.query.trim()
                query.isBlank() || track.displayTitle.contains(query, ignoreCase = true) ||
                    track.displayArtist.contains(query, ignoreCase = true) ||
                    track.displayAlbum.contains(query, ignoreCase = true)
            }
            .toList()

        val sorted = when (current.sort) {
            MusicSort.TITLE -> filtered.sortedWith(compareBy<AudioTrack> { it.displayTitle.lowercase() })
            MusicSort.ARTIST -> filtered.sortedWith(
                compareBy<AudioTrack> { it.displayArtist.lowercase() }
                    .thenBy { it.displayTitle.lowercase() },
            )
            MusicSort.ALBUM -> filtered.sortedWith(
                compareBy<AudioTrack> { it.displayAlbum.lowercase() }
                    .thenBy { it.displayTitle.lowercase() },
            )
            MusicSort.DATE_ADDED -> filtered.sortedBy { it.dateAdded }
            MusicSort.DURATION -> filtered.sortedBy { it.duration }
        }
        val recentAdded = current.allTracks.sortedByDescending { it.dateAdded }.take(12)
        val recentPlayed = preferencesRepository.applicationPreferences.value.musicRecentlyPlayedUris.mapNotNull { uri ->
            current.allTracks.firstOrNull { it.uriString == uri }
        }
        val preferences = preferencesRepository.applicationPreferences.value
        val mostPlayed = current.allTracks
            .filter { (preferences.musicPlayCounts[it.uriString] ?: 0) > 0 }
            .sortedByDescending { preferences.musicPlayCounts[it.uriString] ?: 0 }
            .take(12)
        val favorites = preferences.musicFavorites.mapNotNull { uri ->
            current.allTracks.firstOrNull { it.uriString == uri }
        }
        val albums = current.allTracks
            .groupBy { it.displayAlbum }
            .map { (name, tracks) -> tracks.toCollection(name) }
            .sortedBy { it.name.lowercase() }
        val artists = current.allTracks
            .groupBy { it.displayArtist }
            .map { (name, tracks) -> tracks.toCollection(name) }
            .sortedBy { it.name.lowercase() }
        val folders = current.allTracks
            .groupBy { it.path.substringBeforeLast('/', "") }
            .map { (name, tracks) -> tracks.toCollection(name) }
            .sortedBy { it.name.lowercase() }
        val resumeUri = preferences.musicQueueUris.getOrNull(preferences.musicQueueIndex)
        val resumeTrack = resumeUri
            ?.takeIf { preferences.musicQueuePositionMs > RESUME_THRESHOLD_MS }
            ?.let { uri -> current.allTracks.firstOrNull { it.uriString == uri } }
        stateInternal.update {
            it.copy(
                tracks = if (current.ascending) sorted else sorted.asReversed(),
                recentlyAdded = recentAdded,
                recentlyPlayed = recentPlayed,
                mostPlayed = mostPlayed,
                favorites = favorites,
                favoriteUris = preferences.musicFavorites.toSet(),
                resumeTrack = resumeTrack,
                resumePositionMs = if (resumeTrack != null) preferences.musicQueuePositionMs else 0L,
                albums = albums,
                artists = artists,
                folders = folders,
            )
        }
    }
}

private fun List<AudioTrack>.toCollection(name: String) = MusicCollection(
    name = name,
    trackCount = size,
    artworkUri = firstOrNull { it.artworkUriString != null }?.artworkUriString,
    mediaUri = firstOrNull()?.uriString,
)
