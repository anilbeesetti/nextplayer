package dev.anilbeesetti.nextplayer.feature.videopicker.screens.search

import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.SearchHistoryRepository
import dev.anilbeesetti.nextplayer.core.domain.GetPopularFoldersUseCase
import dev.anilbeesetti.nextplayer.core.domain.SearchMediaUseCase
import dev.anilbeesetti.nextplayer.core.domain.SearchResults
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
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
) : MviViewModel<SearchUiState, SearchUiEvent>() {

    private val uiStateInternal = MutableStateFlow(SearchUiState())
    override val state = uiStateInternal.asStateFlow()

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
                uiStateInternal.update { it.copy(searchHistory = history) }
            }
        }
    }

    private fun collectPopularFolders() {
        viewModelScope.launch {
            getPopularFoldersUseCase(limit = 5).collect { folders ->
                uiStateInternal.update { it.copy(popularFolders = folders) }
            }
        }
    }

    private fun collectPreferences() {
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { prefs ->
                uiStateInternal.update { it.copy(preferences = prefs) }
            }
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun collectSearchResults() {
        viewModelScope.launch {
            searchQuery
                .debounce(SEARCH_DEBOUNCE_MS)
                .flatMapLatest { query ->
                    searchMediaUseCase(query)
                }
                .collect { results ->
                    uiStateInternal.update {
                        it.copy(
                            searchResults = results,
                            isSearching = false,
                        )
                    }
                }
        }
    }

    override fun onAction(action: SearchUiEvent) {
        when (action) {
            is SearchUiEvent.OnQueryChange -> onQueryChange(action.query)
            is SearchUiEvent.OnSearch -> onSearch(action.query)
            is SearchUiEvent.OnHistoryItemClick -> onHistoryItemClick(action.query)
            is SearchUiEvent.OnRemoveHistoryItem -> removeHistoryItem(action.query)
            is SearchUiEvent.OnClearHistory -> clearHistory()
        }
    }

    private fun onQueryChange(query: String) {
        uiStateInternal.update { it.copy(query = query, isSearching = query.isNotBlank()) }
        searchQuery.value = query
    }

    private fun onSearch(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            searchHistoryRepository.addSearchQuery(query)
        }
    }

    private fun onHistoryItemClick(query: String) {
        uiStateInternal.update { it.copy(query = query, isSearching = true) }
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
}
