package dev.anilbeesetti.nextplayer.feature.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val videoRepository: VideoRepository
) : ViewModel() {

    var position = MutableStateFlow<Long?>(0L)
        private set

    fun setCurrentMedia(path: String?) {
        viewModelScope.launch {
            position.value = path?.let { videoRepository.getPosition(it) }
        }
    }

    fun updatePosition(path: String, position: Long) {
        viewModelScope.launch { videoRepository.updatePosition(path, position) }
    }

    fun getPath(uri: Uri): String? {
        return videoRepository.getPath(uri)
    }

    fun getVideos(): List<String> {
        return videoRepository.getAllVideoPaths()
    }

}