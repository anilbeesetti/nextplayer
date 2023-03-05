package dev.anilbeesetti.nextplayer.feature.videopicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.models.Video
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.datastore.AppPreferences
import dev.anilbeesetti.nextplayer.core.datastore.SortBy
import dev.anilbeesetti.nextplayer.core.domain.GetSortedVideosUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VideoPickerViewModel @Inject constructor(
    getSortedVideosUseCase: GetSortedVideosUseCase,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val videoItems = getSortedVideosUseCase.invoke()
        .map { VideosState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = VideosState.Loading
        )

    val preferences = preferencesRepository.preferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppPreferences()
        )

    private val _uiState = MutableStateFlow(VideoPickerViewState())
    val uiState = _uiState.asStateFlow()


    fun updateSortBy(sortBy: SortBy) {
        viewModelScope.launch {
            preferencesRepository.setSortBy(sortBy)
        }
    }

    fun showMenuDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showMenuDialog = show)
    }
}

sealed interface VideosState {
    object Loading : VideosState
    data class Success(val videos: List<Video>) : VideosState
}

data class VideoPickerViewState(
    val showMenuDialog: Boolean = false,
)
