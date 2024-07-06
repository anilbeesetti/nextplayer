package dev.anilbeesetti.nextplayer.settings.screens.decoder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.DecoderPriority
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DecoderPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val preferencesFlow = preferencesRepository.playerPreferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = PlayerPreferences(),
        )

    private val _uiState = MutableStateFlow(DecoderPreferencesUIState())
    val uiState = _uiState.asStateFlow()

    fun onEvent(event: DecoderPreferencesEvent) {
        if (event is DecoderPreferencesEvent.ShowDialog) {
            _uiState.update {
                it.copy(showDialog = event.value)
            }
        }
    }

    fun updateDecoderPriority(value: DecoderPriority) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(decoderPriority = value)
            }
        }
    }
}

data class DecoderPreferencesUIState(
    val showDialog: DecoderPreferenceDialog? = null,
)

sealed interface DecoderPreferenceDialog {
    object DecoderPriorityDialog : DecoderPreferenceDialog
}

sealed interface DecoderPreferencesEvent {
    data class ShowDialog(val value: DecoderPreferenceDialog?) : DecoderPreferencesEvent
}

fun DecoderPreferencesViewModel.showDialog(dialog: DecoderPreferenceDialog) {
    onEvent(DecoderPreferencesEvent.ShowDialog(dialog))
}

fun DecoderPreferencesViewModel.hideDialog() {
    onEvent(DecoderPreferencesEvent.ShowDialog(null))
}
