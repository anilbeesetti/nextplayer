package dev.anilbeesetti.nextplayer.settings.screens.medialibrary

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.common.extensions.collectWhileSubscribed
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = FolderPreferencesViewModel.Factory::class)
class FolderPreferencesViewModel @AssistedInject constructor(
    mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    @Assisted internal var output: Output,
) : MviViewModel<FolderPreferencesUiState, FolderPreferencesUiEvent>() {

    data class Output(
        val navigateUp: () -> Unit,
    )

    @AssistedFactory
    interface Factory {
        fun create(output: Output): FolderPreferencesViewModel
    }

    private val stateInternal = MutableStateFlow(
        FolderPreferencesUiState(
            preferences = preferencesRepository.applicationPreferences.value,
        ),
    )
    override val state: StateFlow<FolderPreferencesUiState> = stateInternal.asStateFlow()

    init {
        mediaRepository.observeFolders().collectWhileSubscribed(viewModelScope, stateInternal) {
            stateInternal.update { currentState ->
                currentState.copy(foldersDataState = DataState.Success(it))
            }
        }

        preferencesRepository.applicationPreferences.collectWhileSubscribed(viewModelScope, stateInternal) { preferences ->
            stateInternal.update { currentState ->
                currentState.copy(preferences = preferences)
            }
        }
    }

    override fun onAction(action: FolderPreferencesUiEvent) {
        when (action) {
            is FolderPreferencesUiEvent.NavigateUp -> output.navigateUp()

            is FolderPreferencesUiEvent.UpdateExcludeList -> updateExcludeList(action.path)
        }
    }

    private fun updateExcludeList(path: String) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(
                    excludeFolders = if (path in it.excludeFolders) {
                        it.excludeFolders - path
                    } else {
                        it.excludeFolders + path
                    },
                )
            }
        }
    }
}

data class FolderPreferencesUiState(
    val foldersDataState: DataState<List<Folder>> = DataState.Loading,
    val preferences: ApplicationPreferences = ApplicationPreferences(),
)

sealed interface FolderPreferencesUiEvent {
    data object NavigateUp : FolderPreferencesUiEvent

    data class UpdateExcludeList(val path: String) : FolderPreferencesUiEvent
}
