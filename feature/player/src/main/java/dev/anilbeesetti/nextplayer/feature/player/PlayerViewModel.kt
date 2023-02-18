package dev.anilbeesetti.nextplayer.feature.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.VideoRepository
import dev.anilbeesetti.nextplayer.core.data.models.PlayerItem
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private const val END_POSITION_OFFSET = 5L

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val videoRepository: VideoRepository
) : ViewModel() {

    var playbackPosition = MutableStateFlow<Long?>(null)
        private set

    var currentPlaybackPath: String? = null
        private set

    var currentPlayerItems: MutableList<PlayerItem> = mutableListOf()

    val currentPlayerItemIndex: Int
        get() = currentPlayerItems.indexOfFirst { it.mediaPath == currentPlaybackPath }

    fun setCurrentMedia(path: String?) {
        currentPlaybackPath = path
        viewModelScope.launch {
            playbackPosition.value = path?.let { videoRepository.getPosition(it) }
        }
    }

    fun initMedia(uri: Uri) {
        setCurrentMedia(getPath(uri))
        currentPlayerItems.addAll(videoRepository.getLocalPlayerItems())
    }

    fun updatePosition(position: Long) {
        playbackPosition.value = position
        viewModelScope.launch {
            currentPlaybackPath?.let {
                if (position >= currentPlayerItems[currentPlayerItemIndex].duration - END_POSITION_OFFSET) {
                    videoRepository.updatePosition(it, C.TIME_UNSET)
                } else {
                    videoRepository.updatePosition(it, position)
                }
            }
        }
    }

    fun updatePosition(path: String, position: Long) {
        viewModelScope.launch { videoRepository.updatePosition(path, position) }
    }

    fun getPath(uri: Uri): String? {
        return videoRepository.getPath(uri)
    }
}
