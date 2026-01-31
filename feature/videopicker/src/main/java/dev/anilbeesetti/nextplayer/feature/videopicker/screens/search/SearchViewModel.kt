package dev.anilbeesetti.nextplayer.feature.videopicker.screens.search

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.SearchHistoryRepository
import dev.anilbeesetti.nextplayer.core.domain.GetPopularFoldersUseCase
import dev.anilbeesetti.nextplayer.core.domain.SearchMediaUseCase
import dev.anilbeesetti.nextplayer.core.domain.SearchResults
import dev.anilbeesetti.nextplayer.core.media.sync.MediaInfoSynchronizer
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Folder
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchMediaUseCase: SearchMediaUseCase,
    private val getPopularFoldersUseCase: GetPopularFoldersUseCase,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val preferencesRepository: PreferencesRepository,
    private val mediaInfoSynchronizer: MediaInfoSynchronizer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState = _uiState.asStateFlow()

    private val searchQuery = MutableStateFlow("")

    init {
        collectSearchHistory()
        collectPopularFolders()
        collectPreferences()
        collectSearchResults()
    }

    private fun collectSearchHistory() {
        viewModelScope.launch {
            searchHistoryRepository.searchHistory.collect { history ->
                _uiState.update { it.copy(searchHistory = history) }
            }
        }
    }

    private fun collectPopularFolders() {
        viewModelScope.launch {
            getPopularFoldersUseCase(limit = 5).collect { folders ->
                _uiState.update { it.copy(popularFolders = folders) }
            }
        }
    }

    private fun collectPreferences() {
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { prefs ->
                _uiState.update { it.copy(preferences = prefs) }
            }
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun collectSearchResults() {
        viewModelScope.launch {
            searchQuery
                .debounce(SEARCH_DEBOUNCE_MS)
                .flatMapLatest { query ->
                    _uiState.update { it.copy(isSearching = query.isNotBlank()) }
                    searchMediaUseCase(query)
                }
                .collect { results ->
                    _uiState.update {
                        it.copy(
                            searchResults = results,
                            isSearching = false,
                        )
                    }
                }
        }
    }

    fun onEvent(event: SearchUiEvent) {
        when (event) {
            is SearchUiEvent.OnQueryChange -> onQueryChange(event.query)
            is SearchUiEvent.OnSearch -> onSearch(event.query)
            is SearchUiEvent.OnHistoryItemClick -> onHistoryItemClick(event.query)
            is SearchUiEvent.OnRemoveHistoryItem -> removeHistoryItem(event.query)
            is SearchUiEvent.OnClearHistory -> clearHistory()
            is SearchUiEvent.AddToSync -> addToMediaInfoSynchronizer(event.uri)
        }
    }

    private fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        searchQuery.value = query
    }

    private fun onSearch(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            searchHistoryRepository.addSearchQuery(query)
        }
    }

    private fun onHistoryItemClick(query: String) {
        _uiState.update { it.copy(query = query) }
        searchQuery.value = query
        onSearch(query)
    }

    private fun removeHistoryItem(query: String) {
        viewModelScope.launch {
            searchHistoryRepository.removeSearchQuery(query)
        }
    }

    private fun clearHistory() {
        viewModelScope.launch {
            searchHistoryRepository.clearHistory()
        }
    }

    private fun addToMediaInfoSynchronizer(uri: Uri) {
        viewModelScope.launch {
            mediaInfoSynchronizer.sync(uri)
        }
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}

@Stable
data class SearchUiState(
    val query: String = "",
    val searchHistory: List<String> = emptyList(),
    val popularFolders: List<Folder> = emptyList(),
    val searchResults: SearchResults = SearchResults(),
    val isSearching: Boolean = false,
    val preferences: ApplicationPreferences = ApplicationPreferences(),
)

sealed interface SearchUiEvent {
    data class OnQueryChange(val query: String) : SearchUiEvent
    data class OnSearch(val query: String) : SearchUiEvent
    data class OnHistoryItemClick(val query: String) : SearchUiEvent
    data class OnRemoveHistoryItem(val query: String) : SearchUiEvent
    data object OnClearHistory : SearchUiEvent
    data class AddToSync(val uri: Uri): SearchUiEvent
}
