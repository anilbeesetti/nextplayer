package dev.anilbeesetti.nextplayer.settings.screens.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class NetworkPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val preferencesFlow = preferencesRepository.playerPreferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = PlayerPreferences()
        )

    private val _uiState = MutableStateFlow(NetworkPreferencesUIState())
    val uiState = _uiState.asStateFlow()

    fun onEvent(event: NetworkPreferencesEvent) {
        if (event is NetworkPreferencesEvent.ShowDialog) {
            _uiState.update {
                it.copy(showDialog = event.value)
            }
        }
    }

    fun updateMinBufferMs(value: Int) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(minBufferMs = value)
            }
        }
    }

    fun updateMaxBufferMs(value: Int) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(maxBufferMs = value)
            }
        }
    }

    fun updateBufferForPlaybackMs(value: Int) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(bufferForPlaybackMs = value)
            }
        }
    }

    fun updateBufferForPlaybackAfterRebufferMs(value: Int) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(bufferForPlaybackAfterRebuffer = value)
            }
        }
    }

    fun updateHttpUserAgent(value: String?) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(httpUserAgent = value)
            }
        }
    }

    fun updateHttpHeaders(value: Map<String, String>) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(httpHeaders = value)
            }
        }
    }
}

data class NetworkPreferencesUIState(
    val showDialog: NetworkPreferenceDialog? = null
)

sealed interface NetworkPreferenceDialog {
    object MinBufferDialog : NetworkPreferenceDialog
    object MaxBufferDialog : NetworkPreferenceDialog
    object BufferForPlaybackDialog : NetworkPreferenceDialog
    object BufferForPlaybackAfterRebufferDialog : NetworkPreferenceDialog
    object HttpUserAgentDialog : NetworkPreferenceDialog
    object HttpHeadersDialog : NetworkPreferenceDialog
}

sealed interface NetworkPreferencesEvent {
    data class ShowDialog(val value: NetworkPreferenceDialog?) : NetworkPreferencesEvent
}

fun NetworkPreferencesViewModel.showDialog(dialog: NetworkPreferenceDialog) {
    onEvent(NetworkPreferencesEvent.ShowDialog(dialog))
}

fun NetworkPreferencesViewModel.hideDialog() {
    onEvent(NetworkPreferencesEvent.ShowDialog(null))
}
