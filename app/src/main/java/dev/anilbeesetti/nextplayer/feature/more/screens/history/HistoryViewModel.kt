package dev.anilbeesetti.nextplayer.feature.more.screens.history

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    preferencesRepository: PreferencesRepository,
) : MviViewModel<HistoryUiState, HistoryAction>() {
    override val state: StateFlow<HistoryUiState> = combine(
        mediaRepository.observePlaybackHistory()
            .map<List<Video>, DataState<List<Video>>> { DataState.Success(it) }
            .catch { emit(DataState.Error(it)) },
        preferencesRepository.applicationPreferences,
    ) { history, preferences ->
        HistoryUiState(history = history, preferences = preferences)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    override fun onAction(action: HistoryAction) {
        when (action) {
            HistoryAction.ClearHistory -> clearHistory()
        }
    }

    private fun clearHistory() {
        viewModelScope.launch { mediaRepository.clearPlaybackHistory() }
    }
}

data class HistoryUiState(
    val history: DataState<List<Video>> = DataState.Loading,
    val preferences: ApplicationPreferences = ApplicationPreferences(),
)

sealed interface HistoryAction {
    data object ClearHistory : HistoryAction
}
