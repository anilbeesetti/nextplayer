package dev.anilbeesetti.nextplayer.feature.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.domain.GetSortedPlaylistUseCase
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

    var playWhenReady: Boolean = true
    var currentVideoScale: Float = 1f
    var skipSilenceEnabled: Boolean = false

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
            externalSubs = listOf(),
        )
    }

    fun addExternalSubtitle(mediaUri: String, subtitleUri: Uri) {
        viewModelScope.launch {
            mediaRepository.addExternalSubtitle(mediaUri = mediaUri, subtitleUri = subtitleUri)
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
}
