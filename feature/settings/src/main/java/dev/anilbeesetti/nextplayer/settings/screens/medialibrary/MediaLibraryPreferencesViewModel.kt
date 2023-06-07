package dev.anilbeesetti.nextplayer.settings.screens.medialibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.Directory
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MediaLibraryPreferencesViewModel @Inject constructor(
    mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val uiState = mediaRepository.getDirectoriesFlow()
        .map { FolderPreferencesUiState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FolderPreferencesUiState.Loading
        )

    fun updateExcludeList(directory: Directory) {
        viewModelScope.launch {
            if (directory.isExcluded) {
                preferencesRepository.updateApplicationPreferences {
                    it.copy(
                        excludeFolders = it.excludeFolders - directory.path
                    )
                }
            } else {
                preferencesRepository.updateApplicationPreferences {
                    it.copy(
                        excludeFolders = it.excludeFolders + directory.path
                    )
                }
            }
        }
    }
}

sealed interface FolderPreferencesUiState {
    object Loading : FolderPreferencesUiState

    data class Success(val directories: List<Directory>) : FolderPreferencesUiState
}
