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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    private val stateInternal = MutableStateFlow(MoreUiState())
    override val state: StateFlow<MoreUiState> = stateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            mediaRepository.observePlaybackHistory()
                .catch { error ->
                    stateInternal.update { it.copy(history = DataState.Error(error)) }
                }
                .collect { history ->
                    stateInternal.update { it.copy(history = DataState.Success(history)) }
                }
        }
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { preferences ->
                stateInternal.update { it.copy(preferences = preferences) }
            }
        }
    }

    override fun onAction(action: MoreAction) {
        when (action) {
            is MoreAction.OpenHistory -> output.openHistory()
            is MoreAction.PlayVideo -> output.playVideo(action.uri)
            is MoreAction.OpenSettings -> output.openSettings()
            is MoreAction.OpenTrash -> output.openTrash()
            is MoreAction.OpenVault -> output.openVault()
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
