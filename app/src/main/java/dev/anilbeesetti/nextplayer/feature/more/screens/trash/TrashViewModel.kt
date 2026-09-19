package dev.anilbeesetti.nextplayer.feature.more.screens.trash

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.media.services.MediaOperationsService
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import dev.anilbeesetti.nextplayer.feature.videopicker.state.SelectionItem
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
class TrashViewModel(
    private val mediaOperationsService: MediaOperationsService,
    mediaRepository: MediaRepository,
    preferencesRepository: PreferencesRepository,
    @InjectedParam internal var output: Output,
) : MviViewModel<TrashUiState, TrashAction>() {

    data class Output(
        val navigateUp: () -> Unit,
        val playVideo: (String) -> Unit,
    )

    override val state: StateFlow<TrashUiState> = combine(
        mediaRepository.observeTrashVideos()
            .map<List<Video>, DataState<List<Video>>> { DataState.Success(it) }
            .onStart { emit(DataState.Loading) }
            .catch { emit(DataState.Error(it)) },
        preferencesRepository.applicationPreferences,
    ) { videos, preferences ->
        TrashUiState(videos = videos, preferences = preferences)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000L),
        initialValue = TrashUiState(),
    )

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
