package dev.anilbeesetti.nextplayer.feature.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.models.VideoState
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.domain.GetSortedPlaylistUseCase
import dev.anilbeesetti.nextplayer.core.model.Resume
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.model.VideoZoom
import dev.anilbeesetti.nextplayer.feature.player.extensions.isSchemaContent
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    private val getSortedPlaylistUseCase: GetSortedPlaylistUseCase,
) : ViewModel() {

    var currentPlaybackPosition: Long? = null
    var currentPlaybackSpeed: Float = 1f
    var currentAudioTrackIndex: Int? = null
    var currentSubtitleTrackIndex: Int? = null
    var currentVideoScale: Float = 1f
    var isPlaybackSpeedChanged: Boolean = false
    val externalSubtitles = mutableSetOf<Uri>()
    var skipSilenceEnabled: Boolean = false

    var currentVideoState: VideoState? = null

    val playerPrefs = preferencesRepository.playerPreferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = runBlocking { preferencesRepository.playerPreferences.first() },
    )

    val appPrefs = preferencesRepository.applicationPreferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = runBlocking { preferencesRepository.applicationPreferences.first() },
    )

    suspend fun initMediaState(uri: String?) {
        if (currentPlaybackPosition != null) return
        currentVideoState = uri?.let { mediaRepository.getVideoState(it) }
        val prefs = playerPrefs.value

        currentPlaybackPosition = currentVideoState?.position.takeIf { prefs.resume == Resume.YES } ?: currentPlaybackPosition
        currentAudioTrackIndex = currentVideoState?.audioTrackIndex.takeIf { prefs.rememberSelections } ?: currentAudioTrackIndex
        currentSubtitleTrackIndex = currentVideoState?.subtitleTrackIndex.takeIf { prefs.rememberSelections } ?: currentSubtitleTrackIndex
        currentPlaybackSpeed = currentVideoState?.playbackSpeed.takeIf { prefs.rememberSelections } ?: prefs.defaultPlaybackSpeed
        currentVideoScale = currentVideoState?.videoScale.takeIf { prefs.rememberSelections } ?: 1f
        externalSubtitles += currentVideoState?.externalSubs ?: emptyList()
    }

    suspend fun getPlaylistFromUri(uri: Uri): List<Video> {
        return getSortedPlaylistUseCase.invoke(uri)
    }

    fun saveMediaUiState(
        uri: Uri,
        videoScale: Float,
    ) {
        currentVideoScale = videoScale

        if (!uri.isSchemaContent) return

        mediaRepository.saveMediumUiState(
            uri = uri.toString(),
            videoScale = videoScale,
            externalSubs = externalSubtitles.toList(),
        )
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
        currentVideoScale = 1f
        skipSilenceEnabled = false
        externalSubtitles.clear()
    }
}
