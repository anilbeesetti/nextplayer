package dev.anilbeesetti.nextplayer.settings.screens.gesture

import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.common.extensions.round
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.DoubleTapGesture
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class GesturePreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : MviViewModel<GesturePreferencesUiState, GesturePreferencesUiEvent>() {

    private val uiStateInternal = MutableStateFlow(
        GesturePreferencesUiState(
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

    override fun onAction(action: GesturePreferencesUiEvent) {
        when (action) {
            is GesturePreferencesUiEvent.ShowDialog -> showDialog(action.value)
            is GesturePreferencesUiEvent.UpdateDoubleTapGesture -> updateDoubleTapGesture(action.gesture)
            GesturePreferencesUiEvent.ToggleUseLongPressControls -> toggleUseLongPressControls()
            GesturePreferencesUiEvent.ToggleDoubleTapGesture -> toggleDoubleTapGesture()
            GesturePreferencesUiEvent.ToggleEnableBrightnessSwipeGesture -> toggleEnableBrightnessSwipeGesture()
            GesturePreferencesUiEvent.ToggleEnableVolumeSwipeGesture -> toggleEnableVolumeSwipeGesture()
            GesturePreferencesUiEvent.ToggleUseSeekControls -> toggleUseSeekControls()
            GesturePreferencesUiEvent.ToggleUseZoomControls -> toggleUseZoomControls()
            GesturePreferencesUiEvent.ToggleEnablePanGesture -> toggleEnablePanGesture()
            is GesturePreferencesUiEvent.UpdateLongPressControlsSpeed -> updateLongPressControlsSpeed(action.value)
            is GesturePreferencesUiEvent.UpdateSeekIncrement -> updateSeekIncrement(action.value)
            is GesturePreferencesUiEvent.UpdateSeekSensitivity -> updateSeekSensitivity(action.value)
            is GesturePreferencesUiEvent.UpdateVolumeGestureSensitivity -> updateVolumeGestureSensitivity(action.value)
            is GesturePreferencesUiEvent.UpdateBrightnessGestureSensitivity -> updateBrightnessGestureSensitivity(action.value)
        }
    }

    private fun showDialog(value: GesturePreferenceDialog?) {
        uiStateInternal.update {
            it.copy(showDialog = value)
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

    private fun updateLongPressControlsSpeed(value: Float) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(longPressControlsSpeed = value) }
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
                it.copy(seekSensitivity = value.round(2))
            }
        }
    }

    private fun updateVolumeGestureSensitivity(value: Float) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(volumeGestureSensitivity = value.round(2))
            }
        }
    }

    private fun updateBrightnessGestureSensitivity(value: Float) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(brightnessGestureSensitivity = value.round(2))
            }
        }
    }
}

@Stable
data class GesturePreferencesUiState(
    val showDialog: GesturePreferenceDialog? = null,
    val preferences: PlayerPreferences = PlayerPreferences(),
)

sealed interface GesturePreferenceDialog {
    data object DoubleTapDialog : GesturePreferenceDialog
    data object LongPressControlsSpeedDialog : GesturePreferenceDialog
}

sealed interface GesturePreferencesUiEvent {
    data class ShowDialog(val value: GesturePreferenceDialog?) : GesturePreferencesUiEvent
    data class UpdateDoubleTapGesture(val gesture: DoubleTapGesture) : GesturePreferencesUiEvent
    data object ToggleUseLongPressControls : GesturePreferencesUiEvent
    data object ToggleDoubleTapGesture : GesturePreferencesUiEvent
    data object ToggleEnableBrightnessSwipeGesture : GesturePreferencesUiEvent
    data object ToggleEnableVolumeSwipeGesture : GesturePreferencesUiEvent
    data object ToggleUseSeekControls : GesturePreferencesUiEvent
    data object ToggleUseZoomControls : GesturePreferencesUiEvent
    data object ToggleEnablePanGesture : GesturePreferencesUiEvent
    data class UpdateLongPressControlsSpeed(val value: Float) : GesturePreferencesUiEvent
    data class UpdateSeekIncrement(val value: Int) : GesturePreferencesUiEvent
    data class UpdateSeekSensitivity(val value: Float) : GesturePreferencesUiEvent
    data class UpdateVolumeGestureSensitivity(val value: Float) : GesturePreferencesUiEvent
    data class UpdateBrightnessGestureSensitivity(val value: Float) : GesturePreferencesUiEvent
}
