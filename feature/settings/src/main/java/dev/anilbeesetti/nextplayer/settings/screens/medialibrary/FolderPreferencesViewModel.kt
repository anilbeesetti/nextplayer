package dev.anilbeesetti.nextplayer.settings.screens.medialibrary

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class FolderPreferencesViewModel @Inject constructor(
    mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
) : MviViewModel<FolderPreferencesUiState, FolderPreferencesUiEvent>() {

    private val uiStateInternal = MutableStateFlow(
        FolderPreferencesUiState(
            preferences = preferencesRepository.applicationPreferences.value,
        ),
    )
    override val state: StateFlow<FolderPreferencesUiState> = uiStateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            mediaRepository.observeFolders().collect {
                uiStateInternal.update { currentState ->
                    currentState.copy(foldersDataState = DataState.Success(it))
                }
            }
        }

        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { preferences ->
                uiStateInternal.update { currentState ->
                    currentState.copy(preferences = preferences)
                }
            }
        }
    }

    override fun onAction(action: FolderPreferencesUiEvent) {
        when (action) {
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
    data class UpdateExcludeList(val path: String) : FolderPreferencesUiEvent
}
