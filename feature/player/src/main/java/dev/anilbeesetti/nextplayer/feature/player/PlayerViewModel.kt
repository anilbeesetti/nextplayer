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

    var playbackPosition = MutableStateFlow<Long?>(null)
        private set

    var currentPlaybackPath: String? = null
        private set

    fun setCurrentMedia(path: String?) {
        currentPlaybackPath = path
        viewModelScope.launch {
            playbackPosition.value = path?.let { videoRepository.getPosition(it) }
        }
    }

    fun updatePosition(position: Long) {
        playbackPosition.value = position
        viewModelScope.launch { currentPlaybackPath?.let { videoRepository.updatePosition(it, position) } }
    }

    fun getPath(uri: Uri): String? {
        return videoRepository.getPath(uri)
    }

    fun getVideos(): List<String> {
        return videoRepository.getAllVideoPaths()
    }

}