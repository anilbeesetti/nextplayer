package dev.anilbeesetti.nextplayer.feature.more.screens.history

import androidx.lifecycle.viewModelScope
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class HistoryViewModel(
    private val mediaRepository: MediaRepository,
    preferencesRepository: PreferencesRepository,
    @InjectedParam internal var output: Output,
) : MviViewModel<HistoryUiState, HistoryAction>() {

    data class Output(
        val navigateUp: () -> Unit,
        val playVideo: (String) -> Unit,
    )

    override val state: StateFlow<HistoryUiState> = combine(
        mediaRepository.observePlaybackHistory()
            .map<List<Video>, DataState<List<Video>>> { DataState.Success(it) }
            .onStart { emit(DataState.Loading) }
            .catch { emit(DataState.Error(it)) },
        preferencesRepository.applicationPreferences,
    ) { history, preferences ->
        HistoryUiState(history = history, preferences = preferences)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000L),
        initialValue = HistoryUiState(),
    )

    override fun onAction(action: HistoryAction) {
        when (action) {
            is HistoryAction.NavigateUp -> output.navigateUp()
            is HistoryAction.PlayVideo -> output.playVideo(action.uri)

            is HistoryAction.ClearHistory -> clearHistory()
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
    data object NavigateUp : HistoryAction
    data class PlayVideo(val uri: String) : HistoryAction

    data object ClearHistory : HistoryAction
}
