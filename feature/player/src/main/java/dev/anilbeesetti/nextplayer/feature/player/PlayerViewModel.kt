package dev.anilbeesetti.nextplayer.feature.player

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.domain.GetSortedPlaylistUseCase
import dev.anilbeesetti.nextplayer.core.model.LoopMode
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.model.VideoContentScale
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import dev.anilbeesetti.nextplayer.feature.player.state.SubtitleOptionsEvent
import dev.anilbeesetti.nextplayer.feature.player.state.VideoZoomEvent
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    private val getSortedPlaylistUseCase: GetSortedPlaylistUseCase,
) : MviViewModel<PlayerUiState, PlayerAction>() {

    data class Output(
        val selectSubtitle: () -> Unit,
        val navigateUp: () -> Unit,
        val playInBackground: () -> Unit,
    )

    private val eventsInternal = Channel<PlayerEvent>(Channel.BUFFERED)
    val events = eventsInternal.receiveAsFlow()

    private val stateInternal = MutableStateFlow(
        PlayerUiState(
            playerPreferences = preferencesRepository.playerPreferences.value,
        ),
    )
    override val state: StateFlow<PlayerUiState> = stateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.playerPreferences.collect { prefs ->
                stateInternal.update { it.copy(playerPreferences = prefs) }
            }
        }
    }

    override fun onAction(action: PlayerAction) {
        when (action) {
            is PlayerAction.SelectSubtitle -> viewModelScope.launch { eventsInternal.send(PlayerEvent.SelectSubtitle) }
            is PlayerAction.NavigateUp -> viewModelScope.launch { eventsInternal.send(PlayerEvent.NavigateUp) }
            is PlayerAction.PlayInBackground -> viewModelScope.launch { eventsInternal.send(PlayerEvent.PlayInBackground) }

            is PlayerAction.UpdatePlayWhenReady -> stateInternal.update { it.copy(playWhenReady = action.value) }
            is PlayerAction.UpdateBrightness -> updatePlayerBrightness(action.value)
            is PlayerAction.UpdateVideoZoom -> onVideoZoomEvent(action.event)
            is PlayerAction.UpdateSubtitleOptions -> onSubtitleOptionEvent(action.event)
            is PlayerAction.SetLoopMode -> setLoopMode(action.loopMode)
        }
    }

    suspend fun getPlaylistFromUri(uri: Uri): List<Video> {
        return getSortedPlaylistUseCase.invoke(uri)
    }

    private fun updateVideoZoom(uri: String, zoom: Float) {
        viewModelScope.launch {
            mediaRepository.updateMediumZoom(uri, zoom)
        }
    }

    private fun updatePlayerBrightness(value: Float) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(playerBrightness = value) }
        }
    }

    private fun updateVideoContentScale(contentScale: VideoContentScale) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(playerVideoZoom = contentScale) }
        }
    }

    private fun setLoopMode(loopMode: LoopMode) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(loopMode = loopMode) }
        }
    }

    private fun onVideoZoomEvent(event: VideoZoomEvent) {
        when (event) {
            is VideoZoomEvent.ContentScaleChanged -> {
                updateVideoContentScale(event.contentScale)
            }
            is VideoZoomEvent.ZoomChanged -> {
                updateVideoZoom(event.mediaItem.mediaId, event.zoom)
            }
        }
    }

    private fun onSubtitleOptionEvent(event: SubtitleOptionsEvent) {
        when (event) {
            is SubtitleOptionsEvent.DelayChanged -> {
                updateSubtitleDelay(event.mediaItem.mediaId, event.delay)
            }
            is SubtitleOptionsEvent.SpeedChanged -> {
                updateSubtitleSpeed(event.mediaItem.mediaId, event.speed)
            }
        }
    }

    private fun updateSubtitleDelay(uri: String, delay: Long) {
        viewModelScope.launch {
            mediaRepository.updateSubtitleDelay(uri, delay)
        }
    }

    private fun updateSubtitleSpeed(uri: String, speed: Float) {
        viewModelScope.launch {
            mediaRepository.updateSubtitleSpeed(uri, speed)
        }
    }
}

@Stable
data class PlayerUiState(
    val playerPreferences: PlayerPreferences? = null,
    val playWhenReady: Boolean = true,
)

sealed interface PlayerAction {
    data object SelectSubtitle : PlayerAction
    data object NavigateUp : PlayerAction
    data object PlayInBackground : PlayerAction

    data class UpdatePlayWhenReady(val value: Boolean) : PlayerAction
    data class UpdateBrightness(val value: Float) : PlayerAction
    data class UpdateVideoZoom(val event: VideoZoomEvent) : PlayerAction
    data class UpdateSubtitleOptions(val event: SubtitleOptionsEvent) : PlayerAction
    data class SetLoopMode(val loopMode: LoopMode) : PlayerAction
}

sealed interface PlayerEvent {
    data object SelectSubtitle : PlayerEvent
    data object NavigateUp : PlayerEvent
    data object PlayInBackground : PlayerEvent
}
