package dev.anilbeesetti.nextplayer.settings.screens.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AudioPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {


    val preferencesFlow = preferencesRepository.playerPreferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PlayerPreferences()
    )

    private val _uiState = MutableStateFlow(AudioPreferencesUIState())
    val uiState = _uiState.asStateFlow()

    fun onEvent(event: AudioPreferencesEvent) {
        if (event is AudioPreferencesEvent.ShowDialog) {
            _uiState.update {
                it.copy(showDialog = event.value)
            }
        }
    }

    fun updateAudioLanguage(value: String) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(preferredAudioLanguage = value)
            }
        }
    }

}

data class AudioPreferencesUIState(
    val showDialog: AudioPreferenceDialog? = null
)

sealed interface AudioPreferenceDialog {
    object AudioLanguageDialog : AudioPreferenceDialog
}

sealed interface AudioPreferencesEvent {
    data class ShowDialog(val value: AudioPreferenceDialog?) : AudioPreferencesEvent
}

fun AudioPreferencesViewModel.showDialog(dialog: AudioPreferenceDialog) {
    onEvent(AudioPreferencesEvent.ShowDialog(dialog))
}

fun AudioPreferencesViewModel.hideDialog() {
    onEvent(AudioPreferencesEvent.ShowDialog(null))
}