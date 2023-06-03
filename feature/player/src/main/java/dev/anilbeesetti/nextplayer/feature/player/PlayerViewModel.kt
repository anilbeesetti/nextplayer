package dev.anilbeesetti.nextplayer.feature.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.VideoRepository
import dev.anilbeesetti.nextplayer.core.domain.GetSortedPlaylistUseCase
import dev.anilbeesetti.nextplayer.core.model.AppPrefs
import dev.anilbeesetti.nextplayer.core.model.PlayerPrefs
import dev.anilbeesetti.nextplayer.core.model.Resume
import dev.anilbeesetti.nextplayer.feature.player.dialogs.getCurrentTrackIndex
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
    var currentPlaybackPosition: Long? = null

    var currentPlaybackSpeed: Float = 1f
        private set

    var currentAudioTrackIndex: Int? = null
        private set

    var currentSubtitleTrackIndex: Int? = null
        private set

    val preferences = preferencesRepository.playerPrefsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PlayerPrefs.default()
    )

    val appPrefs = preferencesRepository.appPrefsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppPrefs.default()
    )

    suspend fun updateInfo(path: String) {
        resetToDefaults()
        val videoState = videoRepository.getVideoState(path) ?: return

        Timber.d("$videoState")

        currentPlaybackPosition =
            videoState.position.takeIf { preferences.value.resume == Resume.YES }
        currentAudioTrackIndex =
            videoState.audioTrack.takeIf { preferences.value.rememberSelections }
        currentSubtitleTrackIndex =
            videoState.subtitleTrack.takeIf { preferences.value.rememberSelections }
        currentPlaybackSpeed =
            videoState.playbackSpeed.takeIf { preferences.value.rememberSelections } ?: 1f
    }

    suspend fun getPlaylistFromUri(uri: Uri): List<Uri> {
        return getSortedPlaylistUseCase.invoke(uri)
    }

    fun saveState(path: String?, player: Player) {
        currentPlaybackPosition = player.currentPosition

        if (path == null) return

        val position = player.currentPosition.takeIf {
            player.currentPosition < player.duration - END_POSITION_OFFSET
        } ?: C.TIME_UNSET
        val audioTrack = player.getCurrentTrackIndex(C.TRACK_TYPE_AUDIO)
        val subtitleTrack = player.getCurrentTrackIndex(C.TRACK_TYPE_TEXT)
        val playbackSpeed = player.playbackParameters.speed

        viewModelScope.launch {
            Timber.d("Save state for $path: $position, $audioTrack, $subtitleTrack, $playbackSpeed")
            videoRepository.saveVideoState(
                path = path,
                position = position,
                audioTrackIndex = audioTrack,
                subtitleTrackIndex = subtitleTrack,
                playbackSpeed = playbackSpeed
            )
        }
    }

    fun setPlayerBrightness(value: Float) {
        viewModelScope.launch {
            preferencesRepository.setPlayerBrightness(value)
        }
    }

    fun resetToDefaults() {
        currentPlaybackPosition = null
        currentPlaybackSpeed = 1f
        currentAudioTrackIndex = null
        currentSubtitleTrackIndex = null
    }
}
