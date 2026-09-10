package dev.anilbeesetti.nextplayer.settings.screens.player

import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.common.extensions.round
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.ControlButtonsPosition
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.Resume
import dev.anilbeesetti.nextplayer.core.model.ScreenOrientation
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PlayerPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : MviViewModel<PlayerPreferencesUiState, PlayerPreferencesUiEvent>() {

    private val uiStateInternal = MutableStateFlow(
        PlayerPreferencesUiState(
            preferences = preferencesRepository.playerPreferences.value,
        ),
    )
    override val state = uiStateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.playerPreferences.collect { preferences ->
                uiStateInternal.update { it.copy(preferences = preferences) }
            }
        }
    }

    override fun onAction(action: PlayerPreferencesUiEvent) {
        when (action) {
            is PlayerPreferencesUiEvent.ShowDialog -> showDialog(action.value)
            is PlayerPreferencesUiEvent.UpdatePlaybackResume -> updatePlaybackResume(action.resume)
            PlayerPreferencesUiEvent.ToggleAutoplay -> toggleAutoplay()
            PlayerPreferencesUiEvent.ToggleAutoPip -> toggleAutoPip()
            PlayerPreferencesUiEvent.ToggleAutoBackgroundPlay -> toggleAutoBackgroundPlay()
            PlayerPreferencesUiEvent.ToggleRememberBrightnessLevel -> toggleRememberBrightnessLevel()
            PlayerPreferencesUiEvent.ToggleRememberSelections -> toggleRememberSelections()
            is PlayerPreferencesUiEvent.UpdatePreferredPlayerOrientation -> updatePreferredPlayerOrientation(action.value)
            is PlayerPreferencesUiEvent.UpdatePreferredControlButtonsPosition -> updatePreferredControlButtonsPosition(action.value)
            is PlayerPreferencesUiEvent.UpdateDefaultPlaybackSpeed -> updateDefaultPlaybackSpeed(action.value)
            is PlayerPreferencesUiEvent.UpdateControlAutoHideTimeout -> updateControlAutoHideTimeout(action.value)
            PlayerPreferencesUiEvent.ToggleUseMaterialYouControls -> toggleUseMaterialYouControls()
        }
    }

    private fun showDialog(value: PlayerPreferenceDialog?) {
        uiStateInternal.update {
            it.copy(showDialog = value)
        }
    }

    private fun updatePlaybackResume(resume: Resume) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(
                    resume = resume,
                )
            }
        }
    }

    private fun toggleAutoplay() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(autoplay = !it.autoplay)
            }
        }
    }

    private fun toggleAutoPip() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(autoPip = !it.autoPip)
            }
        }
    }

    private fun toggleAutoBackgroundPlay() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(autoBackgroundPlay = !it.autoBackgroundPlay)
            }
        }
    }

    private fun toggleRememberBrightnessLevel() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(rememberPlayerBrightness = !it.rememberPlayerBrightness)
            }
        }
    }

    private fun toggleRememberSelections() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(rememberSelections = !it.rememberSelections)
            }
        }
    }

    private fun updatePreferredPlayerOrientation(value: ScreenOrientation) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(playerScreenOrientation = value)
            }
        }
    }

    private fun updatePreferredControlButtonsPosition(value: ControlButtonsPosition) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(controlButtonsPosition = value)
            }
        }
    }

    private fun updateDefaultPlaybackSpeed(value: Float) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(defaultPlaybackSpeed = value.round(1))
            }
        }
    }

    private fun updateControlAutoHideTimeout(value: Int) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(controllerAutoHideTimeout = value)
            }
        }
    }

    private fun toggleUseMaterialYouControls() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(useMaterialYouControls = !it.useMaterialYouControls)
            }
        }
    }
}

@Stable
data class PlayerPreferencesUiState(
    val showDialog: PlayerPreferenceDialog? = null,
    val preferences: PlayerPreferences = PlayerPreferences(),
)

sealed interface PlayerPreferenceDialog {
    data object ResumeDialog : PlayerPreferenceDialog
    data object PlayerScreenOrientationDialog : PlayerPreferenceDialog
    data object ControlButtonsDialog : PlayerPreferenceDialog
}

sealed interface PlayerPreferencesUiEvent {
    data class ShowDialog(val value: PlayerPreferenceDialog?) : PlayerPreferencesUiEvent
    data class UpdatePlaybackResume(val resume: Resume) : PlayerPreferencesUiEvent
    data object ToggleAutoplay : PlayerPreferencesUiEvent
    data object ToggleAutoPip : PlayerPreferencesUiEvent
    data object ToggleAutoBackgroundPlay : PlayerPreferencesUiEvent
    data object ToggleRememberBrightnessLevel : PlayerPreferencesUiEvent
    data object ToggleRememberSelections : PlayerPreferencesUiEvent
    data class UpdatePreferredPlayerOrientation(val value: ScreenOrientation) : PlayerPreferencesUiEvent
    data class UpdatePreferredControlButtonsPosition(val value: ControlButtonsPosition) : PlayerPreferencesUiEvent
    data class UpdateDefaultPlaybackSpeed(val value: Float) : PlayerPreferencesUiEvent
    data class UpdateControlAutoHideTimeout(val value: Int) : PlayerPreferencesUiEvent
    data object ToggleUseMaterialYouControls : PlayerPreferencesUiEvent
}
