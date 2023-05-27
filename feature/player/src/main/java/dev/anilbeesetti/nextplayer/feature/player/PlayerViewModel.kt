package dev.anilbeesetti.nextplayer.feature.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.VideoRepository
import dev.anilbeesetti.nextplayer.core.datastore.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.datastore.Resume
import dev.anilbeesetti.nextplayer.core.domain.GetSortedPlaylistUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

private const val END_POSITION_OFFSET = 5L

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
    private val preferencesRepository: PreferencesRepository,
    private val getSortedPlaylistUseCase: GetSortedPlaylistUseCase
) : ViewModel() {
    var currentPlaybackPosition = MutableStateFlow<Long?>(null)
        private set

    var currentPlaybackSpeed = MutableStateFlow(1f)
        private set

    var currentAudioTrackIndex = MutableStateFlow<Int?>(null)
        private set

    var currentSubtitleTrackIndex = MutableStateFlow<Int?>(null)
        private set

    val preferences = preferencesRepository.playerPreferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PlayerPreferences()
    )

    fun updateInfo(path: String) {
        viewModelScope.launch {
            val videoState = videoRepository.getVideoState(path) ?: return@launch

            currentPlaybackPosition.value = videoState.position.takeIf { preferences.value.resume == Resume.YES }
            currentAudioTrackIndex.value = videoState.audioTrack.takeIf { preferences.value.rememberSelections }
            currentSubtitleTrackIndex.value = videoState.subtitleTrack.takeIf { preferences.value.rememberSelections }
            currentPlaybackSpeed.value = videoState.playbackSpeed.takeIf { preferences.value.rememberSelections } ?: 1f
        }
    }

    suspend fun getPlaylistFromUri(uri: Uri): List<Uri> {
        return getSortedPlaylistUseCase.invoke(uri)
    }

    fun saveState(path: String?, position: Long, duration: Long) {
        currentPlaybackPosition.value = position
        viewModelScope.launch {
            if (path == null) return@launch
            val newPosition = position.takeIf {
                position < duration - END_POSITION_OFFSET
            } ?: C.TIME_UNSET

            Timber.d("Save state for $path: $position")

            videoRepository.saveVideoState(
                path = path,
                position = newPosition,
                audioTrackIndex = currentAudioTrackIndex.value,
                subtitleTrackIndex = currentSubtitleTrackIndex.value,
                playbackSpeed = currentPlaybackSpeed.value
            )
        }
    }

    fun switchTrack(trackType: @C.TrackType Int, trackIndex: Int) {
        when (trackType) {
            C.TRACK_TYPE_AUDIO -> currentAudioTrackIndex.value = trackIndex
            C.TRACK_TYPE_TEXT -> currentSubtitleTrackIndex.value = trackIndex
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        currentPlaybackSpeed.value = speed
    }

    fun setPlayerBrightness(value: Float) {
        viewModelScope.launch {
            preferencesRepository.setPlayerBrightness(value)
        }
    }

    fun resetToDefaults() {
        currentPlaybackPosition.value = null
        currentPlaybackSpeed.value = 1f
        currentAudioTrackIndex.value = null
        currentSubtitleTrackIndex.value = null
    }
}
