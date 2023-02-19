package dev.anilbeesetti.nextplayer.feature.videopicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.models.VideoItem
import dev.anilbeesetti.nextplayer.core.data.repository.VideoRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class VideoPickerViewModel @Inject constructor(
    videoRepository: VideoRepository
) : ViewModel() {

    val videoItems = videoRepository.getVideoItemsFlow()
        .map { VideoPickerUiState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = VideoPickerUiState.Loading
        )
}

sealed interface VideoPickerUiState {
    object Loading : VideoPickerUiState
    data class Success(val videoItems: List<VideoItem>) : VideoPickerUiState
}
