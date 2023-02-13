package dev.anilbeesetti.nextplayer

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class MediaPickerViewModel @Inject constructor(
    private val mediaManager: MediaManager
) : ViewModel() {

    private val _mediaPickerUiState = MutableStateFlow(MediaPickerUiState())
    val mediaPickerUiState = _mediaPickerUiState.asStateFlow()

    fun scanMedia() {
        _mediaPickerUiState.update {
            it.copy(videos = mediaManager.getVideos())
        }
    }
}

data class MediaPickerUiState(
    val videos: List<MediaItem> = emptyList()
)
