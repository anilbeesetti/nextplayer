package com.graviton.feature.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.graviton.core.data.repository.MusicRepository
import com.graviton.core.data.repository.PreferencesRepository
import com.graviton.core.model.AudioTrack
import com.graviton.core.model.MusicPlaylist
import com.graviton.core.model.lastMusicUriForFolder
import com.graviton.core.model.recordMusicPlay
import com.graviton.core.model.startIndexForFolderPlayback
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Sections shown in the music library. The names also describe the actual MediaStore query used. */
enum class MusicSection(val label: String) {
    HOME("Home"),
    TRACKS("Tracks"),
    PLAYLISTS("Playlists"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    FOLDERS("Folders"),
}

enum class MusicSort(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    DATE_ADDED("Recently added"),
    DURATION("Duration"),
}

sealed interface MusicFilter {
    data object None : MusicFilter
    data class Album(val name: String) : MusicFilter
    data class Artist(val name: String) : MusicFilter
    data class Folder(val path: String) : MusicFilter
    data class Playlist(val id: Long, val name: String, val trackIds: Set<Long> = emptySet()) : MusicFilter
}

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
    val query: String = "",
    val sort: MusicSort = MusicSort.TITLE,
    val ascending: Boolean = true,
    val filter: MusicFilter = MusicFilter.None,
    val isFilterLoading: Boolean = false,
)

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {
    private val stateInternal = MutableStateFlow(MusicUiState())
    val uiState = stateInternal.asStateFlow()

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

    fun recordPlay(track: AudioTrack) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.recordMusicPlay(track.uriString, track.path.substringBeforeLast('/', ""))
            }
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
        stateInternal.update {
            it.copy(
                tracks = if (current.ascending) sorted else sorted.asReversed(),
                recentlyAdded = recentAdded,
                recentlyPlayed = recentPlayed,
            )
        }
    }
}
