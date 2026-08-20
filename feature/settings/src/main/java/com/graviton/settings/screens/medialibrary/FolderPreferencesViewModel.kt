package com.graviton.settings.screens.medialibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.graviton.core.data.repository.MediaRepository
import com.graviton.core.data.repository.PreferencesRepository
import com.graviton.core.model.ApplicationPreferences
import com.graviton.core.model.Folder
import com.graviton.core.ui.base.DataState
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
) : ViewModel() {

    private val uiStateInternal = MutableStateFlow(
        FolderPreferencesUiState(
            preferences = preferencesRepository.applicationPreferences.value,
        ),
    )
    val uiState: StateFlow<FolderPreferencesUiState> = uiStateInternal.asStateFlow()

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

    fun onEvent(event: FolderPreferencesUiEvent) {
        when (event) {
            is FolderPreferencesUiEvent.UpdateExcludeList -> updateExcludeList(event.path)
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
