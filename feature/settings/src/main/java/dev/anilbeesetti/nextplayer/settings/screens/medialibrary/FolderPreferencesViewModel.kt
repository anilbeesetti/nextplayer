package dev.anilbeesetti.nextplayer.settings.screens.medialibrary

import androidx.lifecycle.viewModelScope
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class FolderPreferencesViewModel(
    mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    @InjectedParam internal var output: Output,
) : MviViewModel<FolderPreferencesUiState, FolderPreferencesUiEvent>() {

    data class Output(
        val navigateUp: () -> Unit,
    )

    override val state: StateFlow<FolderPreferencesUiState> = combine(
        mediaRepository.observeFolders()
            .map<List<Folder>, DataState<List<Folder>>> { DataState.Success(it) }
            .onStart { emit(DataState.Loading) }
            .catch { emit(DataState.Error(it)) },
        preferencesRepository.applicationPreferences,
    ) { folders, preferences ->
        FolderPreferencesUiState(foldersDataState = folders, preferences = preferences)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000L),
        initialValue = FolderPreferencesUiState(preferences = preferencesRepository.applicationPreferences.value),
    )

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
