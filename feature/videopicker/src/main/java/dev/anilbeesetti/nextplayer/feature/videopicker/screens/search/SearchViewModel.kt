package dev.anilbeesetti.nextplayer.feature.videopicker.screens.search

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.SearchHistoryRepository
import dev.anilbeesetti.nextplayer.core.domain.GetPopularFoldersUseCase
import dev.anilbeesetti.nextplayer.core.domain.SearchMediaUseCase
import dev.anilbeesetti.nextplayer.core.domain.SearchResults
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class SearchViewModel(
    private val searchMediaUseCase: SearchMediaUseCase,
    private val getPopularFoldersUseCase: GetPopularFoldersUseCase,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val preferencesRepository: PreferencesRepository,
    @InjectedParam internal var output: Output,
) : MviViewModel<SearchUiState, SearchUiEvent>() {

    data class Output(
        val navigateUp: () -> Unit,
        val playVideo: (Uri) -> Unit,
        val openFolder: (String) -> Unit,
    )

    private val searchQuery = MutableStateFlow("")

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val searchResults = searchQuery
        .debounce(SEARCH_DEBOUNCE_MS)
        .flatMapLatest { query ->
            searchMediaUseCase(query).map { query to it }
        }
        .onStart { emit("" to SearchResults()) }

    override val state: StateFlow<SearchUiState> = combine(
        searchQuery,
        searchResults,
        searchHistoryRepository.searchHistory.onStart { emit(emptyList()) },
        getPopularFoldersUseCase(limit = 5).onStart { emit(emptyList()) },
        preferencesRepository.applicationPreferences,
    ) { query, results, history, folders, preferences ->
        SearchUiState(
            query = query,
            searchResults = results.second,
            isSearching = query.isNotBlank() && query != results.first,
            searchHistory = history,
            popularFolders = folders,
            preferences = preferences,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000L),
        initialValue = SearchUiState(),
    )

    override fun onAction(action: SearchUiEvent) {
        when (action) {
            is SearchUiEvent.NavigateUp -> output.navigateUp()
            is SearchUiEvent.PlayVideo -> output.playVideo(action.uri)
            is SearchUiEvent.OpenFolder -> output.openFolder(action.path)

            is SearchUiEvent.OnQueryChange -> onQueryChange(action.query)
            is SearchUiEvent.OnSearch -> onSearch(action.query)
            is SearchUiEvent.OnHistoryItemClick -> onHistoryItemClick(action.query)
            is SearchUiEvent.OnRemoveHistoryItem -> removeHistoryItem(action.query)
            is SearchUiEvent.OnClearHistory -> clearHistory()
        }
    }

    private fun onQueryChange(query: String) {
        searchQuery.value = query
    }

    private fun onSearch(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            searchHistoryRepository.addSearchQuery(query)
        }
    }

    private fun onHistoryItemClick(query: String) {
        onQueryChange(query)
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
    data object NavigateUp : SearchUiEvent
    data class PlayVideo(val uri: Uri) : SearchUiEvent
    data class OpenFolder(val path: String) : SearchUiEvent

    data class OnQueryChange(val query: String) : SearchUiEvent
    data class OnSearch(val query: String) : SearchUiEvent
    data class OnHistoryItemClick(val query: String) : SearchUiEvent
    data class OnRemoveHistoryItem(val query: String) : SearchUiEvent
    data object OnClearHistory : SearchUiEvent
}
