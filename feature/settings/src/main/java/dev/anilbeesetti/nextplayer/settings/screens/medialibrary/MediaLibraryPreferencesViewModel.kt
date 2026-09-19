package dev.anilbeesetti.nextplayer.settings.screens.medialibrary

import androidx.lifecycle.viewModelScope
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    override val state: StateFlow<MediaLibraryPreferencesUiState> = preferencesRepository.applicationPreferences
        .map { MediaLibraryPreferencesUiState(preferences = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000L),
            initialValue = MediaLibraryPreferencesUiState(preferencesRepository.applicationPreferences.value),
        )

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
