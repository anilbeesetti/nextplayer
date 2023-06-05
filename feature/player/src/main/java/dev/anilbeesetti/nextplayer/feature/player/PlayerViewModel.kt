package dev.anilbeesetti.nextplayer.feature.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.VideoRepository
import dev.anilbeesetti.nextplayer.core.domain.GetSortedPlaylistUseCase
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.Resume
import javax.inject.Inject
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

    var currentAudioTrackIndex: Int? = null

    var currentSubtitleTrackIndex: Int? = null

    val currentExternalSubtitles = mutableListOf<Uri>()

    val preferences = preferencesRepository.playerPreferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PlayerPreferences()
    )

    val appPrefs = preferencesRepository.applicationPreferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ApplicationPreferences()
    )

    suspend fun updateState(path: String, shouldUpdateSubtitles: Boolean) {
        resetToDefaults(exceptSubtitles = !shouldUpdateSubtitles)
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

        // TODO: update subs when stored in local storage
    }

    suspend fun getPlaylistFromUri(uri: Uri): List<Uri> {
        return getSortedPlaylistUseCase.invoke(uri)
    }

    fun saveState(
        path: String?,
        position: Long,
        duration: Long,
        audioTrackIndex: Int,
        subtitleTrackIndex: Int,
        playbackSpeed: Float
    ) {
        currentPlaybackPosition = position
        currentAudioTrackIndex = audioTrackIndex
        currentSubtitleTrackIndex = subtitleTrackIndex
        currentPlaybackSpeed = playbackSpeed

        if (path == null) return

        val newPosition = position.takeIf {
            position < duration - END_POSITION_OFFSET
        } ?: C.TIME_UNSET

        viewModelScope.launch {
            videoRepository.saveVideoState(
                path = path,
                position = newPosition,
                audioTrackIndex = audioTrackIndex,
                subtitleTrackIndex = subtitleTrackIndex,
                playbackSpeed = playbackSpeed
            )
        }
    }

    fun setPlayerBrightness(value: Float) {
        viewModelScope.launch {
            preferencesRepository.setPlayerBrightness(value)
        }
    }

    fun resetToDefaults(exceptSubtitles: Boolean = false) {
        currentPlaybackPosition = null
        currentPlaybackSpeed = 1f
        currentAudioTrackIndex = null
        currentSubtitleTrackIndex = null
        if (!exceptSubtitles) currentExternalSubtitles.clear()
    }
}
