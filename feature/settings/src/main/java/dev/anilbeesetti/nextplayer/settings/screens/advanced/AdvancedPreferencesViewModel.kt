package dev.anilbeesetti.nextplayer.settings.screens.advanced

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
class AdvancedPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val preferencesFlow = preferencesRepository.playerPreferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = PlayerPreferences()
        )

    private val _uiState = MutableStateFlow(AdvancedPreferencesUIState())
    val uiState = _uiState.asStateFlow()

    fun onEvent(event: AdvancedPreferencesEvent) {
        if (event is AdvancedPreferencesEvent.ShowDialog) {
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

data class AdvancedPreferencesUIState(
    val showDialog: AdvancedPreferenceDialog? = null
)

sealed interface AdvancedPreferenceDialog {
    object MinBufferDialog : AdvancedPreferenceDialog
    object MaxBufferDialog : AdvancedPreferenceDialog
    object BufferForPlaybackDialog : AdvancedPreferenceDialog
    object BufferForPlaybackAfterRebufferDialog : AdvancedPreferenceDialog
    object HttpUserAgentDialog : AdvancedPreferenceDialog
    object HttpHeadersDialog : AdvancedPreferenceDialog
}

sealed interface AdvancedPreferencesEvent {
    data class ShowDialog(val value: AdvancedPreferenceDialog?) : AdvancedPreferencesEvent
}

fun AdvancedPreferencesViewModel.showDialog(dialog: AdvancedPreferenceDialog) {
    onEvent(AdvancedPreferencesEvent.ShowDialog(dialog))
}

fun AdvancedPreferencesViewModel.hideDialog() {
    onEvent(AdvancedPreferencesEvent.ShowDialog(null))
}
