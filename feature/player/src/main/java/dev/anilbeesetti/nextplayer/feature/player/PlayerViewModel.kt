package dev.anilbeesetti.nextplayer.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.VideoRepository
import dev.anilbeesetti.nextplayer.core.datastore.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.datastore.Resume
import dev.anilbeesetti.nextplayer.core.domain.GetSortedPlayerItemsUseCase
import dev.anilbeesetti.nextplayer.core.domain.model.PlayerItem
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

private const val END_POSITION_OFFSET = 5L

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
    private val preferencesRepository: PreferencesRepository,
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

    val preferences = preferencesRepository.playerPreferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PlayerPreferences()
    )

    fun setCurrentMedia(path: String?) {
        currentPlaybackPath.value = path
        viewModelScope.launch {
            path?.let {
                val videoState = videoRepository.getVideoState(it)
                Timber.d("Get state for $it: $videoState")
                playbackPosition.value = if (preferences.value.resume == Resume.YES) videoState?.position else null
                currentAudioTrackIndex.value = videoState?.audioTrack
                currentSubtitleTrackIndex.value = videoState?.subtitleTrack
            }
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

    fun saveState(position: Long) {
        playbackPosition.value = position
        viewModelScope.launch {
            currentPlaybackPath.value?.let {
                val newPosition = position.takeIf {
                    position < currentPlayerItems[currentPlayerItemIndex].duration - END_POSITION_OFFSET
                } ?: C.TIME_UNSET

                videoRepository.saveVideoState(
                    path = it,
                    position = newPosition,
                    audioTrackIndex = currentAudioTrackIndex.value,
                    subtitleTrackIndex = currentSubtitleTrackIndex.value
                )
            }
        }
    }

    fun saveState(path: String, position: Long) {
        viewModelScope.launch {
            videoRepository.saveVideoState(
                path = path,
                position = position,
                audioTrackIndex = currentAudioTrackIndex.value,
                subtitleTrackIndex = currentAudioTrackIndex.value
            )
        }
    }

    fun switchTrack(trackType: @C.TrackType Int, trackIndex: Int) {
        when (trackType) {
            C.TRACK_TYPE_AUDIO -> currentAudioTrackIndex.value = trackIndex
            C.TRACK_TYPE_TEXT -> currentSubtitleTrackIndex.value = trackIndex
        }
    }

    fun setPlayerBrightness(value: Float) {
        viewModelScope.launch {
            preferencesRepository.setPlayerBrightness(value)
        }
    }
}
