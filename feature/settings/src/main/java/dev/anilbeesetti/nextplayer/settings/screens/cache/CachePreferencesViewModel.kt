package dev.anilbeesetti.nextplayer.settings.screens.cache

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.StreamCacheClearPolicy
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CachePreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val preferencesFlow = preferencesRepository.playerPreferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PlayerPreferences(),
    )

    private val _uiState = MutableStateFlow(CachePreferencesUIState())
    val uiState = _uiState.asStateFlow()

    fun onEvent(event: CachePreferencesEvent) {
        if (event is CachePreferencesEvent.ShowDialog) {
            _uiState.update {
                it.copy(showDialog = event.value)
            }
        }
    }

    fun updateStreamCacheClearPolicy(policy: StreamCacheClearPolicy) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(streamCacheClearPolicy = policy)
            }
        }
    }

    fun updateBufferSettings(
        minBufferMs: Int,
        maxBufferMs: Int,
        bufferForPlaybackMs: Int,
        bufferForPlaybackAfterRebufferMs: Int,
        rangeStreamChunkSizeBytes: Long,
        segmentConcurrentDownloads: Int,
    ) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(
                    minBufferMs = minBufferMs,
                    maxBufferMs = maxBufferMs,
                    bufferForPlaybackMs = bufferForPlaybackMs,
                    bufferForPlaybackAfterRebufferMs = bufferForPlaybackAfterRebufferMs,
                    rangeStreamChunkSizeBytes = rangeStreamChunkSizeBytes,
                    segmentConcurrentDownloads = segmentConcurrentDownloads,
                )
            }
        }
    }
}

data class CachePreferencesUIState(
    val showDialog: CachePreferenceDialog? = null,
)

sealed interface CachePreferenceDialog {
    object CacheClearPolicyDialog : CachePreferenceDialog
    object BufferSettingsDialog : CachePreferenceDialog
}

sealed interface CachePreferencesEvent {
    data class ShowDialog(val value: CachePreferenceDialog?) : CachePreferencesEvent
}

fun CachePreferencesViewModel.showDialog(dialog: CachePreferenceDialog) {
    onEvent(CachePreferencesEvent.ShowDialog(dialog))
}

fun CachePreferencesViewModel.hideDialog() {
    onEvent(CachePreferencesEvent.ShowDialog(null))
}
