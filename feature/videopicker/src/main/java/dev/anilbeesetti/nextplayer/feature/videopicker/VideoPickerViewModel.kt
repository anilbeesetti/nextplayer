package dev.anilbeesetti.nextplayer.feature.videopicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class VideoPickerViewModel @Inject constructor(
    private val mediaManager: MediaManager
) : ViewModel() {

    private val _videoPickerUiState = MutableStateFlow(VideoPickerUiState())
    val videoPickerUiState = _videoPickerUiState.asStateFlow()

    fun scanMedia() {
        viewModelScope.launch {
            _videoPickerUiState.update {
                it.copy(videoItems = mediaManager.getVideos())
            }
        }
    }
}

data class VideoPickerUiState(
    val videoItems: List<VideoItem> = emptyList()
)
