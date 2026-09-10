package dev.anilbeesetti.nextplayer.settings.screens.medialibrary

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = MediaLibraryPreferencesViewModel.Factory::class)
class MediaLibraryPreferencesViewModel @AssistedInject constructor(
    private val preferencesRepository: PreferencesRepository,
    @Assisted internal var output: Output,
) : MviViewModel<MediaLibraryPreferencesUiState, MediaLibraryPreferencesUiEvent>() {

    data class Output(
        val navigateUp: () -> Unit,
        val openFolders: () -> Unit,
        val openThumbnails: () -> Unit,
    )

    @AssistedFactory
    interface Factory {
        fun create(output: Output): MediaLibraryPreferencesViewModel
    }

    private val stateInternal = MutableStateFlow(MediaLibraryPreferencesUiState())
    override val state: StateFlow<MediaLibraryPreferencesUiState> = stateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect {
                stateInternal.update { currentState ->
                    currentState.copy(preferences = it)
                }
            }
        }
    }

    override fun onAction(action: MediaLibraryPreferencesUiEvent) {
        when (action) {
            is MediaLibraryPreferencesUiEvent.NavigateUp -> output.navigateUp()
            is MediaLibraryPreferencesUiEvent.OpenFolders -> output.openFolders()
            is MediaLibraryPreferencesUiEvent.OpenThumbnails -> output.openThumbnails()

            is MediaLibraryPreferencesUiEvent.ToggleMarkLastPlayedMedia -> toggleMarkLastPlayedMedia()
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
    data object NavigateUp : MediaLibraryPreferencesUiEvent
    data object OpenFolders : MediaLibraryPreferencesUiEvent
    data object OpenThumbnails : MediaLibraryPreferencesUiEvent

    data object ToggleMarkLastPlayedMedia : MediaLibraryPreferencesUiEvent
}
