package dev.anilbeesetti.nextplayer.feature.more.screens.trash

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.media.services.MediaOperationsService
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.feature.videopicker.state.SelectionItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val mediaOperationsService: MediaOperationsService,
    mediaRepository: MediaRepository,
    preferencesRepository: PreferencesRepository,
) : ViewModel() {
    val uiState: StateFlow<TrashUiState> = combine(
        mediaRepository.observeTrashVideos()
            .map<List<Video>, DataState<List<Video>>> { DataState.Success(it) }
            .catch { emit(DataState.Error(it)) },
        preferencesRepository.applicationPreferences,
    ) { videos, preferences ->
        TrashUiState(videos = videos, preferences = preferences)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrashUiState())

    fun restore(selectionItems: Set<SelectionItem>) {
        viewModelScope.launch { mediaOperationsService.restoreMedia(selectionItems.toUris()) }
    }

    fun deletePermanently(selectionItems: Set<SelectionItem>) {
        viewModelScope.launch { mediaOperationsService.deleteMedia(selectionItems.toUris(), permanently = true) }
    }

    private fun Set<SelectionItem>.toUris(): List<Uri> = filterIsInstance<SelectionItem.Video>().map { it.uriString.toUri() }
}

data class TrashUiState(
    val videos: DataState<List<Video>> = DataState.Loading,
    val preferences: ApplicationPreferences = ApplicationPreferences(),
)
