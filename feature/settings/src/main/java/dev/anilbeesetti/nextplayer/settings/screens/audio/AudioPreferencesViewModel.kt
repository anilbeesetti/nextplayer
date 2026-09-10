package dev.anilbeesetti.nextplayer.settings.screens.audio

import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = AudioPreferencesViewModel.Factory::class)
class AudioPreferencesViewModel @AssistedInject constructor(
    private val preferencesRepository: PreferencesRepository,
    @Assisted internal var output: Output,
) : MviViewModel<AudioPreferencesUiState, AudioPreferencesUiEvent>() {

    data class Output(
        val navigateUp: () -> Unit,
    )

    @AssistedFactory
    interface Factory {
        fun create(output: Output): AudioPreferencesViewModel
    }

    private val stateInternal = MutableStateFlow(
        AudioPreferencesUiState(
            preferences = preferencesRepository.playerPreferences.value,
        ),
    )
    override val state: StateFlow<AudioPreferencesUiState> = stateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.playerPreferences.collect { preferences ->
                stateInternal.update { currentState ->
                    currentState.copy(preferences = preferences)
                }
            }
        }
    }

    override fun onAction(action: AudioPreferencesUiEvent) {
        when (action) {
            is AudioPreferencesUiEvent.NavigateUp -> output.navigateUp()

            is AudioPreferencesUiEvent.ShowDialog -> showDialog(action.value)
            is AudioPreferencesUiEvent.UpdateAudioLanguage -> updateAudioLanguage(action.value)
            is AudioPreferencesUiEvent.TogglePauseOnHeadsetDisconnect -> togglePauseOnHeadsetDisconnect()
            is AudioPreferencesUiEvent.ToggleShowSystemVolumePanel -> toggleShowSystemVolumePanel()
            is AudioPreferencesUiEvent.ToggleRequireAudioFocus -> toggleRequireAudioFocus()
            is AudioPreferencesUiEvent.ToggleVolumeBoost -> toggleVolumeBoost()
        }
    }

    private fun showDialog(value: AudioPreferenceDialog?) {
        stateInternal.update {
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
    data object NavigateUp : AudioPreferencesUiEvent

    data class ShowDialog(val value: AudioPreferenceDialog?) : AudioPreferencesUiEvent
    data class UpdateAudioLanguage(val value: String) : AudioPreferencesUiEvent
    data object TogglePauseOnHeadsetDisconnect : AudioPreferencesUiEvent
    data object ToggleShowSystemVolumePanel : AudioPreferencesUiEvent
    data object ToggleRequireAudioFocus : AudioPreferencesUiEvent
    data object ToggleVolumeBoost : AudioPreferencesUiEvent
}
