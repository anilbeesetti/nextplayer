package dev.anilbeesetti.nextplayer.feature.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.models.VideoState
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.domain.GetSortedPlaylistUseCase
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.Resume
import dev.anilbeesetti.nextplayer.core.model.VideoZoom
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

private const val END_POSITION_OFFSET = 5L

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    private val getSortedPlaylistUseCase: GetSortedPlaylistUseCase
) : ViewModel() {

    var currentPlaybackPosition: Long? = null
    var currentPlaybackSpeed: Float = 1f
    var currentAudioTrackIndex: Int? = null
    var currentSubtitleTrackIndex: Int? = null
    var isPlaybackSpeedChanged: Boolean = false
    val externalSubtitles = mutableSetOf<Uri>()

    private var currentVideoState: VideoState? = null

    val playerPrefs = preferencesRepository.playerPreferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PlayerPreferences()
    )

    val appPrefs = preferencesRepository.applicationPreferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ApplicationPreferences()
    )

    suspend fun updateState(path: String?) {
        currentVideoState = path?.let { mediaRepository.getVideoState(it) }

        Timber.d("$currentVideoState")

        val prefs = playerPrefs.value
        currentPlaybackPosition = currentVideoState?.position.takeIf { prefs.resume == Resume.YES } ?: currentPlaybackPosition
        currentAudioTrackIndex = currentVideoState?.audioTrackIndex.takeIf { prefs.rememberSelections } ?: currentAudioTrackIndex
        currentSubtitleTrackIndex = currentVideoState?.subtitleTrackIndex.takeIf { prefs.rememberSelections } ?: currentSubtitleTrackIndex
        currentPlaybackSpeed = currentVideoState?.playbackSpeed.takeIf { prefs.rememberSelections } ?: prefs.defaultPlaybackSpeed
        externalSubtitles += currentVideoState?.externalSubs ?: emptyList()
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

        if (path == null) {
            saveCurrentNetworkPlaybackPosition(position)
            return
        }

        val newPosition = position.takeIf {
            position < duration - END_POSITION_OFFSET
        } ?: C.TIME_UNSET

        viewModelScope.launch {
            mediaRepository.saveVideoState(
                path = path,
                position = newPosition,
                audioTrackIndex = audioTrackIndex,
                subtitleTrackIndex = subtitleTrackIndex,
                playbackSpeed = playbackSpeed.takeIf { isPlaybackSpeedChanged } ?: currentVideoState?.playbackSpeed,
                externalSubs = externalSubtitles.toList()
            )
        }
    }

    fun saveCurrentPlayback(uri: Uri) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(lastPlayback = uri.toString())
            }
        }
    }

    private fun saveCurrentNetworkPlaybackPosition(position: Long) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(lastNetworkPlaybackPosition = position)
            }
        }
    }

    fun setPlayerBrightness(value: Float) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(playerBrightness = value) }
        }
    }

    fun setVideoZoom(videoZoom: VideoZoom) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(playerVideoZoom = videoZoom) }
        }
    }

    fun resetAllToDefaults() {
        currentPlaybackPosition = null
        currentPlaybackSpeed = 1f
        currentAudioTrackIndex = null
        currentSubtitleTrackIndex = null
        isPlaybackSpeedChanged = false
        externalSubtitles.clear()
    }
}
