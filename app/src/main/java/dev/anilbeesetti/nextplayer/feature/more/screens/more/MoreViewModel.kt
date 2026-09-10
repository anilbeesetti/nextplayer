package dev.anilbeesetti.nextplayer.feature.more.screens.more

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
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
import kotlinx.coroutines.flow.stateIn

@HiltViewModel(assistedFactory = MoreViewModel.Factory::class)
class MoreViewModel @AssistedInject constructor(
    mediaRepository: MediaRepository,
    preferencesRepository: PreferencesRepository,
    @Assisted internal var output: Output,
) : MviViewModel<MoreUiState, MoreAction>() {

    @AssistedFactory
    interface Factory {
        fun create(output: Output): MoreViewModel
    }

    override val state: StateFlow<MoreUiState> = combine(
        mediaRepository.observePlaybackHistory()
            .map<List<Video>, DataState<List<Video>>> { DataState.Success(it) }
            .catch { emit(DataState.Error(it)) },
        preferencesRepository.applicationPreferences,
    ) { history, preferences ->
        MoreUiState(history = history, preferences = preferences)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoreUiState())

    override fun onAction(action: MoreAction) {
        when (action) {
            MoreAction.OpenHistory -> output.openHistory()
            is MoreAction.PlayVideo -> output.playVideo(action.uri)
            MoreAction.OpenSettings -> output.openSettings()
            MoreAction.OpenTrash -> output.openTrash()
            MoreAction.OpenVault -> output.openVault()
        }
    }

    data class Output(
        val openHistory: () -> Unit,
        val playVideo: (String) -> Unit,
        val openSettings: () -> Unit,
        val openTrash: () -> Unit,
        val openVault: () -> Unit,
    )
}

data class MoreUiState(
    val history: DataState<List<Video>> = DataState.Loading,
    val preferences: ApplicationPreferences = ApplicationPreferences(),
)

sealed interface MoreAction {
    data object OpenHistory : MoreAction
    data class PlayVideo(val uri: String) : MoreAction
    data object OpenSettings : MoreAction
    data object OpenTrash : MoreAction
    data object OpenVault : MoreAction
}
