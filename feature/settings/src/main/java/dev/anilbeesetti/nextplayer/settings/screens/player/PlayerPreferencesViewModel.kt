package dev.anilbeesetti.nextplayer.settings.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.DoubleTapGesture
import dev.anilbeesetti.nextplayer.core.model.FastSeek
import dev.anilbeesetti.nextplayer.core.model.PlayerPrefs
import dev.anilbeesetti.nextplayer.core.model.Resume
import dev.anilbeesetti.nextplayer.core.model.ScreenOrientation
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PlayerPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val preferencesFlow = preferencesRepository.playerPrefsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = PlayerPrefs.default()
        )

    private val _uiState = MutableStateFlow(PlayerPreferencesUIState())
    val uiState = _uiState.asStateFlow()

    fun onEvent(event: PlayerPreferencesEvent) {
        if (event is PlayerPreferencesEvent.ShowDialog) {
            _uiState.update {
                it.copy(showDialog = event.value)
            }
        }
    }

    fun updatePlaybackResume(resume: Resume) {
        viewModelScope.launch { preferencesRepository.setPlaybackResume(resume) }
    }

    fun updateDoubleTapGesture(gesture: DoubleTapGesture) {
        viewModelScope.launch { preferencesRepository.setDoubleTapGesture(gesture) }
    }

    fun updateFastSeek(fastSeek: FastSeek) {
        viewModelScope.launch { preferencesRepository.setFastSeek(fastSeek) }
    }

    fun toggleDoubleTapGesture() {
        viewModelScope.launch {
            preferencesRepository.setDoubleTapGesture(
                if (preferencesFlow.value.doubleTapGesture == DoubleTapGesture.NONE) {
                    DoubleTapGesture.FAST_FORWARD_AND_REWIND
                } else {
                    DoubleTapGesture.NONE
                }
            )
        }
    }

    fun toggleFastSeek() {
        viewModelScope.launch {
            preferencesRepository.setFastSeek(
                if (preferencesFlow.value.fastSeek == FastSeek.DISABLE) {
                    FastSeek.AUTO
                } else {
                    FastSeek.DISABLE
                }
            )
        }
    }

    fun toggleRememberBrightnessLevel() {
        viewModelScope.launch {
            preferencesRepository.shouldRememberPlayerBrightness(
                !preferencesFlow.value.rememberPlayerBrightness
            )
        }
    }

    fun toggleSwipeControls() {
        viewModelScope.launch {
            preferencesRepository.setUseSwipeControls(
                !preferencesFlow.value.useSwipeControls
            )
        }
    }

    fun toggleRememberSelections() {
        viewModelScope.launch {
            preferencesRepository.setRememberSelections(
                !preferencesFlow.value.rememberSelections
            )
        }
    }

    fun toggleSeekControls() {
        viewModelScope.launch {
            preferencesRepository.setUseSeekControls(
                !preferencesFlow.value.useSeekControls
            )
        }
    }

    fun updateAudioLanguage(value: String) {
        viewModelScope.launch {
            preferencesRepository.setPreferredAudioLanguage(value)
        }
    }

    fun updateSubtitleLanguage(value: String) {
        viewModelScope.launch {
            preferencesRepository.setPreferredSubtitleLanguage(value)
        }
    }

    fun updatePreferredPlayerOrientation(value: ScreenOrientation) {
        viewModelScope.launch {
            preferencesRepository.setPlayerScreenOrientation(value)
        }
    }
}

data class PlayerPreferencesUIState(
    val showDialog: PlayerPreferenceDialog = PlayerPreferenceDialog.None
)

sealed interface PlayerPreferenceDialog {
    object ResumeDialog : PlayerPreferenceDialog
    object DoubleTapDialog : PlayerPreferenceDialog
    object FastSeekDialog : PlayerPreferenceDialog
    object AudioLanguageDialog : PlayerPreferenceDialog
    object SubtitleLanguageDialog : PlayerPreferenceDialog
    object PlayerScreenOrientationDialog : PlayerPreferenceDialog
    object None : PlayerPreferenceDialog
}

sealed interface PlayerPreferencesEvent {
    data class ShowDialog(val value: PlayerPreferenceDialog) : PlayerPreferencesEvent
}

fun PlayerPreferencesViewModel.showDialog(dialog: PlayerPreferenceDialog) {
    onEvent(PlayerPreferencesEvent.ShowDialog(dialog))
}

fun PlayerPreferencesViewModel.hideDialog() {
    onEvent(PlayerPreferencesEvent.ShowDialog(PlayerPreferenceDialog.None))
}
