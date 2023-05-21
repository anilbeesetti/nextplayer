package dev.anilbeesetti.nextplayer.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.models.VideoState
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.VideoRepository
import dev.anilbeesetti.nextplayer.core.datastore.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.domain.GetPlayerItemFromPathUseCase
import dev.anilbeesetti.nextplayer.core.domain.GetSortedPlayerItemsUseCase
import dev.anilbeesetti.nextplayer.core.domain.model.PlayerItem
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

private const val END_POSITION_OFFSET = 5L

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
    private val preferencesRepository: PreferencesRepository,
    private val getSortedPlayerItemsUseCase: GetSortedPlayerItemsUseCase,
    private val getPlayerItemFromPathUseCase: GetPlayerItemFromPathUseCase
) : ViewModel() {

    var playbackPosition = MutableStateFlow<Long?>(null)
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

    suspend fun getVideoState(path: String): VideoState? {
        return videoRepository.getVideoState(path)
    }

    fun updateInfo(playerItem: PlayerItem) {
        viewModelScope.launch {
            val videoState = videoRepository.getVideoState(playerItem.path) ?: return@launch

            playbackPosition.value = videoState.position
            currentAudioTrackIndex.value = videoState.audioTrack
            currentSubtitleTrackIndex.value = videoState.subtitleTrack
        }
    }

    fun getPlayerItemFromPath(path: String?): PlayerItem? {
        return getPlayerItemFromPathUseCase.invoke(path)
    }

    suspend fun getPlayerItemsFromPath(path: String?): List<PlayerItem> {
        val parent = path?.let { File(it).parent }
        return getSortedPlayerItemsUseCase.invoke(parent).first()
    }

    fun saveState(playerItem: PlayerItem?, position: Long) {
        playbackPosition.value = position
        viewModelScope.launch {
            if (playerItem == null) return@launch
            val newPosition = position.takeIf {
                position < playerItem.duration - END_POSITION_OFFSET
            } ?: C.TIME_UNSET

            Timber.d("Save state for ${playerItem.path}: $position")

            videoRepository.saveVideoState(
                path = playerItem.path,
                position = newPosition,
                audioTrackIndex = currentAudioTrackIndex.value,
                subtitleTrackIndex = currentSubtitleTrackIndex.value,
                rememberSelections = preferences.value.rememberSelections
            )
        }
    }

    fun saveState(path: String, position: Long) {
        viewModelScope.launch {
            videoRepository.saveVideoState(
                path = path,
                position = position,
                audioTrackIndex = currentAudioTrackIndex.value,
                subtitleTrackIndex = currentAudioTrackIndex.value,
                rememberSelections = preferences.value.rememberSelections
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
