package dev.anilbeesetti.nextplayer.settings.screens.audio

import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AudioPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : MviViewModel<AudioPreferencesUiState, AudioPreferencesUiEvent>() {

    private val uiStateInternal = MutableStateFlow(
        AudioPreferencesUiState(
            preferences = preferencesRepository.playerPreferences.value,
        ),
    )
    override val state = uiStateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.playerPreferences.collect { preferences ->
                uiStateInternal.update { currentState ->
                    currentState.copy(preferences = preferences)
                }
            }
        }
    }

    override fun onAction(action: AudioPreferencesUiEvent) {
        when (action) {
            is AudioPreferencesUiEvent.ShowDialog -> showDialog(action.value)
            is AudioPreferencesUiEvent.UpdateAudioLanguage -> updateAudioLanguage(action.value)
            AudioPreferencesUiEvent.TogglePauseOnHeadsetDisconnect -> togglePauseOnHeadsetDisconnect()
            AudioPreferencesUiEvent.ToggleShowSystemVolumePanel -> toggleShowSystemVolumePanel()
            AudioPreferencesUiEvent.ToggleRequireAudioFocus -> toggleRequireAudioFocus()
            AudioPreferencesUiEvent.ToggleVolumeBoost -> toggleVolumeBoost()
        }
    }

    private fun showDialog(value: AudioPreferenceDialog?) {
        uiStateInternal.update {
            it.copy(showDialog = value)
        }
    }

    private fun updateAudioLanguage(value: String) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(preferredAudioLanguage = value)
            }
        }
    }

    private fun togglePauseOnHeadsetDisconnect() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(pauseOnHeadsetDisconnect = !it.pauseOnHeadsetDisconnect)
            }
        }
    }

    private fun toggleShowSystemVolumePanel() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(showSystemVolumePanel = !it.showSystemVolumePanel)
            }
        }
    }

    private fun toggleRequireAudioFocus() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(requireAudioFocus = !it.requireAudioFocus)
            }
        }
    }

    private fun toggleVolumeBoost() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(enableVolumeBoost = !it.enableVolumeBoost)
            }
        }
    }
}

@Stable
data class AudioPreferencesUiState(
    val showDialog: AudioPreferenceDialog? = null,
    val preferences: PlayerPreferences = PlayerPreferences(),
)

sealed interface AudioPreferenceDialog {
    data object AudioLanguageDialog : AudioPreferenceDialog
}

sealed interface AudioPreferencesUiEvent {
    data class ShowDialog(val value: AudioPreferenceDialog?) : AudioPreferencesUiEvent
    data class UpdateAudioLanguage(val value: String) : AudioPreferencesUiEvent
    data object TogglePauseOnHeadsetDisconnect : AudioPreferencesUiEvent
    data object ToggleShowSystemVolumePanel : AudioPreferencesUiEvent
    data object ToggleRequireAudioFocus : AudioPreferencesUiEvent
    data object ToggleVolumeBoost : AudioPreferencesUiEvent
}
