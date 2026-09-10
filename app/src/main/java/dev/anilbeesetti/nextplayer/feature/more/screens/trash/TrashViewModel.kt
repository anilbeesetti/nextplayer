package dev.anilbeesetti.nextplayer.feature.more.screens.trash

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.media.services.MediaOperationsService
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import dev.anilbeesetti.nextplayer.feature.videopicker.state.SelectionItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = TrashViewModel.Factory::class)
class TrashViewModel @AssistedInject constructor(
    private val mediaOperationsService: MediaOperationsService,
    mediaRepository: MediaRepository,
    preferencesRepository: PreferencesRepository,
    @Assisted internal var output: Output,
) : MviViewModel<TrashUiState, TrashAction>() {

    data class Output(
        val navigateUp: () -> Unit,
        val playVideo: (String) -> Unit,
    )

    @AssistedFactory
    interface Factory {
        fun create(output: Output): TrashViewModel
    }

    private val stateInternal = MutableStateFlow(TrashUiState())
    override val state: StateFlow<TrashUiState> = stateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            mediaRepository.observeTrashVideos()
                .catch { error ->
                    stateInternal.update { it.copy(videos = DataState.Error(error)) }
                }
                .collect { videos ->
                    stateInternal.update { it.copy(videos = DataState.Success(videos)) }
                }
        }
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { preferences ->
                stateInternal.update { it.copy(preferences = preferences) }
            }
        }
    }

    override fun onAction(action: TrashAction) {
        when (action) {
            is TrashAction.NavigateUp -> output.navigateUp()
            is TrashAction.PlayVideo -> output.playVideo(action.uri)

            is TrashAction.Restore -> restore(action.selectionItems)
            is TrashAction.DeletePermanently -> deletePermanently(action.selectionItems)
        }
    }

    private fun restore(selectionItems: Set<SelectionItem>) {
        viewModelScope.launch { mediaOperationsService.restoreMedia(selectionItems.toUris()) }
    }

    private fun deletePermanently(selectionItems: Set<SelectionItem>) {
        viewModelScope.launch { mediaOperationsService.deleteMedia(selectionItems.toUris(), permanently = true) }
    }

    private fun Set<SelectionItem>.toUris(): List<Uri> = filterIsInstance<SelectionItem.Video>().map { it.uriString.toUri() }
}

data class TrashUiState(
    val videos: DataState<List<Video>> = DataState.Loading,
    val preferences: ApplicationPreferences = ApplicationPreferences(),
)

sealed interface TrashAction {
    data object NavigateUp : TrashAction
    data class PlayVideo(val uri: String) : TrashAction

    data class Restore(val selectionItems: Set<SelectionItem>) : TrashAction
    data class DeletePermanently(val selectionItems: Set<SelectionItem>) : TrashAction
}
