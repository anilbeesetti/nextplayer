package dev.anilbeesetti.nextplayer.settings.screens.decoder

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.DecoderPriority
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DecoderPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val uiStateInternal = MutableStateFlow(
        DecoderPreferencesUiState(
            preferences = preferencesRepository.playerPreferences.value,
        ),
    )
    val uiState = uiStateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.playerPreferences.collect { preferences ->
                uiStateInternal.update { currentState ->
                    currentState.copy(preferences = preferences)
                }
            }
        }
    }

    fun onEvent(event: DecoderPreferencesUiEvent) {
        when (event) {
            is DecoderPreferencesUiEvent.ShowDialog -> showDialog(event.value)
            is DecoderPreferencesUiEvent.UpdateDecoderPriority -> updateDecoderPriority(event.value)
        }
    }

    private fun showDialog(value: DecoderPreferenceDialog?) {
        uiStateInternal.update {
            it.copy(showDialog = value)
        }
    }

    private fun updateDecoderPriority(value: DecoderPriority) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(decoderPriority = value)
            }
        }
    }
}

@Stable
data class DecoderPreferencesUiState(
    val showDialog: DecoderPreferenceDialog? = null,
    val preferences: PlayerPreferences = PlayerPreferences(),
)

sealed interface DecoderPreferenceDialog {
    data object DecoderPriorityDialog : DecoderPreferenceDialog
}

sealed interface DecoderPreferencesUiEvent {
    data class ShowDialog(val value: DecoderPreferenceDialog?) : DecoderPreferencesUiEvent
    data class UpdateDecoderPriority(val value: DecoderPriority) : DecoderPreferencesUiEvent
}
