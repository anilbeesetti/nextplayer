package dev.anilbeesetti.nextplayer.settings.screens.medialibrary

import androidx.lifecycle.viewModelScope
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class MediaLibraryPreferencesViewModel(
    private val preferencesRepository: PreferencesRepository,
    @InjectedParam internal var output: Output,
) : MviViewModel<MediaLibraryPreferencesUiState, MediaLibraryPreferencesUiEvent>() {

    data class Output(
        val navigateUp: () -> Unit,
        val openFolders: () -> Unit,
        val openThumbnails: () -> Unit,
    )

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
            is MediaLibraryPreferencesUiEvent.ShowNewVideoThresholdDialog -> {
                stateInternal.update { it.copy(showNewVideoThresholdDialog = action.show) }
            }
            is MediaLibraryPreferencesUiEvent.UpdateNewVideoThreshold -> updateNewVideoThreshold(action.days)
        }
    }

    private fun toggleMarkLastPlayedMedia() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(markLastPlayedMedia = !it.markLastPlayedMedia)
            }
        }
    }

    private fun updateNewVideoThreshold(days: Int) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(newVideoThresholdDays = days)
            }
        }
    }
}

data class MediaLibraryPreferencesUiState(
    val preferences: ApplicationPreferences = ApplicationPreferences(),
    val showNewVideoThresholdDialog: Boolean = false,
)

sealed interface MediaLibraryPreferencesUiEvent {
    data object NavigateUp : MediaLibraryPreferencesUiEvent
    data object OpenFolders : MediaLibraryPreferencesUiEvent
    data object OpenThumbnails : MediaLibraryPreferencesUiEvent

    data object ToggleMarkLastPlayedMedia : MediaLibraryPreferencesUiEvent
    data class ShowNewVideoThresholdDialog(val show: Boolean) : MediaLibraryPreferencesUiEvent
    data class UpdateNewVideoThreshold(val days: Int) : MediaLibraryPreferencesUiEvent
}
