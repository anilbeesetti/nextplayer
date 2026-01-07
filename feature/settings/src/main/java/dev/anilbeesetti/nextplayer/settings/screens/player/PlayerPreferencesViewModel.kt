package dev.anilbeesetti.nextplayer.settings.screens.player

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.common.extensions.round
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.ControlButtonsPosition
import dev.anilbeesetti.nextplayer.core.model.DoubleTapGesture
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.Resume
import dev.anilbeesetti.nextplayer.core.model.ScreenOrientation
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PlayerPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val uiStateInternal = MutableStateFlow(
        PlayerPreferencesUiState(
            preferences = preferencesRepository.playerPreferences.value
        )
    )
    val uiState = uiStateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.playerPreferences.collect { preferences ->
                uiStateInternal.update { it.copy(preferences = preferences) }
            }
        }
    }

    fun onEvent(event: PlayerPreferencesUiEvent) {
        when (event) {
            is PlayerPreferencesUiEvent.ShowDialog -> showDialog(event.value)
            is PlayerPreferencesUiEvent.UpdatePlaybackResume -> updatePlaybackResume(event.resume)
            is PlayerPreferencesUiEvent.UpdateDoubleTapGesture -> updateDoubleTapGesture(event.gesture)
            PlayerPreferencesUiEvent.ToggleUseLongPressControls -> toggleUseLongPressControls()
            PlayerPreferencesUiEvent.ToggleDoubleTapGesture -> toggleDoubleTapGesture()
            PlayerPreferencesUiEvent.ToggleAutoplay -> toggleAutoplay()
            PlayerPreferencesUiEvent.ToggleAutoPip -> toggleAutoPip()
            PlayerPreferencesUiEvent.ToggleAutoBackgroundPlay -> toggleAutoBackgroundPlay()
            PlayerPreferencesUiEvent.ToggleRememberBrightnessLevel -> toggleRememberBrightnessLevel()
            PlayerPreferencesUiEvent.ToggleEnableBrightnessSwipeGesture -> toggleEnableBrightnessSwipeGesture()
            PlayerPreferencesUiEvent.ToggleEnableVolumeSwipeGesture -> toggleEnableVolumeSwipeGesture()
            PlayerPreferencesUiEvent.ToggleUseSeekControls -> toggleUseSeekControls()
            PlayerPreferencesUiEvent.ToggleUseZoomControls -> toggleUseZoomControls()
            PlayerPreferencesUiEvent.ToggleEnablePanGesture -> toggleEnablePanGesture()
            PlayerPreferencesUiEvent.ToggleRememberSelections -> toggleRememberSelections()
            is PlayerPreferencesUiEvent.UpdatePreferredPlayerOrientation -> updatePreferredPlayerOrientation(event.value)
            is PlayerPreferencesUiEvent.UpdatePreferredControlButtonsPosition -> updatePreferredControlButtonsPosition(event.value)
            is PlayerPreferencesUiEvent.UpdateDefaultPlaybackSpeed -> updateDefaultPlaybackSpeed(event.value)
            is PlayerPreferencesUiEvent.UpdateLongPressControlsSpeed -> updateLongPressControlsSpeed(event.value)
            is PlayerPreferencesUiEvent.UpdateControlAutoHideTimeout -> updateControlAutoHideTimeout(event.value)
            is PlayerPreferencesUiEvent.UpdateSeekIncrement -> updateSeekIncrement(event.value)
            is PlayerPreferencesUiEvent.UpdateSeekSensitivity -> updateSeekSensitivity(event.value)
            PlayerPreferencesUiEvent.ToggleHidePlayerButtonsBackground -> toggleHidePlayerButtonsBackground()
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

    private fun updateDoubleTapGesture(gesture: DoubleTapGesture) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(doubleTapGesture = gesture)
            }
        }
    }

    private fun toggleUseLongPressControls() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(useLongPressControls = !it.useLongPressControls)
            }
        }
    }

    private fun toggleDoubleTapGesture() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(
                    doubleTapGesture = if (it.doubleTapGesture == DoubleTapGesture.NONE) {
                        DoubleTapGesture.FAST_FORWARD_AND_REWIND
                    } else {
                        DoubleTapGesture.NONE
                    },
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

    private fun toggleEnableBrightnessSwipeGesture() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(enableBrightnessSwipeGesture = !it.enableBrightnessSwipeGesture)
            }
        }
    }

    private fun toggleEnableVolumeSwipeGesture() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(enableVolumeSwipeGesture = !it.enableVolumeSwipeGesture)
            }
        }
    }

    private fun toggleUseSeekControls() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(useSeekControls = !it.useSeekControls)
            }
        }
    }

    private fun toggleUseZoomControls() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(useZoomControls = !it.useZoomControls)
            }
        }
    }

    private fun toggleEnablePanGesture() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(enablePanGesture = !it.enablePanGesture)
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

    private fun updateLongPressControlsSpeed(value: Float) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(longPressControlsSpeed = value) }
        }
    }

    private fun updateControlAutoHideTimeout(value: Int) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(controllerAutoHideTimeout = value)
            }
        }
    }

    private fun updateSeekIncrement(value: Int) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(seekIncrement = value)
            }
        }
    }

    private fun updateSeekSensitivity(value: Float) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(seekSensitivity = value.round(1))
            }
        }
    }

    private fun toggleHidePlayerButtonsBackground() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(hidePlayerButtonsBackground = !it.hidePlayerButtonsBackground)
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
    data object DoubleTapDialog : PlayerPreferenceDialog
    data object PlayerScreenOrientationDialog : PlayerPreferenceDialog
    data object ControlButtonsDialog : PlayerPreferenceDialog
    data object LongPressControlsSpeedDialog : PlayerPreferenceDialog
}

sealed interface PlayerPreferencesUiEvent {
    data class ShowDialog(val value: PlayerPreferenceDialog?) : PlayerPreferencesUiEvent
    data class UpdatePlaybackResume(val resume: Resume) : PlayerPreferencesUiEvent
    data class UpdateDoubleTapGesture(val gesture: DoubleTapGesture) : PlayerPreferencesUiEvent
    data object ToggleUseLongPressControls : PlayerPreferencesUiEvent
    data object ToggleDoubleTapGesture : PlayerPreferencesUiEvent
    data object ToggleAutoplay : PlayerPreferencesUiEvent
    data object ToggleAutoPip : PlayerPreferencesUiEvent
    data object ToggleAutoBackgroundPlay : PlayerPreferencesUiEvent
    data object ToggleRememberBrightnessLevel : PlayerPreferencesUiEvent
    data object ToggleEnableBrightnessSwipeGesture : PlayerPreferencesUiEvent
    data object ToggleEnableVolumeSwipeGesture : PlayerPreferencesUiEvent
    data object ToggleUseSeekControls : PlayerPreferencesUiEvent
    data object ToggleUseZoomControls : PlayerPreferencesUiEvent
    data object ToggleEnablePanGesture : PlayerPreferencesUiEvent
    data object ToggleRememberSelections : PlayerPreferencesUiEvent
    data class UpdatePreferredPlayerOrientation(val value: ScreenOrientation) : PlayerPreferencesUiEvent
    data class UpdatePreferredControlButtonsPosition(val value: ControlButtonsPosition) : PlayerPreferencesUiEvent
    data class UpdateDefaultPlaybackSpeed(val value: Float) : PlayerPreferencesUiEvent
    data class UpdateLongPressControlsSpeed(val value: Float) : PlayerPreferencesUiEvent
    data class UpdateControlAutoHideTimeout(val value: Int) : PlayerPreferencesUiEvent
    data class UpdateSeekIncrement(val value: Int) : PlayerPreferencesUiEvent
    data class UpdateSeekSensitivity(val value: Float) : PlayerPreferencesUiEvent
    data object ToggleHidePlayerButtonsBackground : PlayerPreferencesUiEvent
}
