package dev.anilbeesetti.nextplayer.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.VideoRepository
import dev.anilbeesetti.nextplayer.core.domain.GetSortedPlayerItemsUseCase
import dev.anilbeesetti.nextplayer.core.domain.model.PlayerItem
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val END_POSITION_OFFSET = 5L

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
    private val getSortedPlayerItemsUseCase: GetSortedPlayerItemsUseCase
) : ViewModel() {

    var playbackPosition = MutableStateFlow<Long?>(null)
        private set

    var currentPlaybackPath = MutableStateFlow<String?>(null)
        private set

    var currentPlayerItems: MutableList<PlayerItem> = mutableListOf()

    val currentPlayerItemIndex: Int
        get() = currentPlayerItems.indexOfFirst { it.path == currentPlaybackPath.value }

    var currentAudioTrackIndex = MutableStateFlow<Int?>(null)
        private set

    var currentSubtitleTrackIndex = MutableStateFlow<Int?>(null)
        private set

    fun setCurrentMedia(path: String?) {
        currentPlaybackPath.value = path
        viewModelScope.launch {
            playbackPosition.value = path?.let { videoRepository.getPosition(it) }
            currentAudioTrackIndex.value = null
            currentSubtitleTrackIndex.value = null
        }
    }

    fun initMedia(path: String?) {
        setCurrentMedia(path)
        runBlocking {
            currentPlayerItems.addAll(
                getSortedPlayerItemsUseCase.invoke().first()
            )
        }
    }

    fun updatePosition(position: Long) {
        playbackPosition.value = position
        viewModelScope.launch {
            currentPlaybackPath.value?.let {
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

    fun switchTrack(trackType: @C.TrackType Int, trackIndex: Int) {
        when (trackType) {
            C.TRACK_TYPE_AUDIO -> currentAudioTrackIndex.value = trackIndex
            C.TRACK_TYPE_TEXT -> currentSubtitleTrackIndex.value = trackIndex
        }
    }
}
