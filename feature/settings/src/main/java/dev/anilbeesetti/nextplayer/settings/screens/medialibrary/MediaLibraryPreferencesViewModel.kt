package dev.anilbeesetti.nextplayer.settings.screens.medialibrary

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MediaLibraryPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : MviViewModel<MediaLibraryPreferencesUiState, MediaLibraryPreferencesUiEvent>() {

    private val uiStateInternal = MutableStateFlow(MediaLibraryPreferencesUiState())
    override val state: StateFlow<MediaLibraryPreferencesUiState> = uiStateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect {
                uiStateInternal.update { currentState ->
                    currentState.copy(preferences = it)
                }
            }
        }
    }

    override fun onAction(action: MediaLibraryPreferencesUiEvent) {
        when (action) {
            MediaLibraryPreferencesUiEvent.ToggleMarkLastPlayedMedia -> toggleMarkLastPlayedMedia()
        }
    }

    private fun toggleMarkLastPlayedMedia() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(markLastPlayedMedia = !it.markLastPlayedMedia)
            }
        }
    }
}

data class MediaLibraryPreferencesUiState(
    val preferences: ApplicationPreferences = ApplicationPreferences(),
)

sealed interface MediaLibraryPreferencesUiEvent {
    data object ToggleMarkLastPlayedMedia : MediaLibraryPreferencesUiEvent
}
