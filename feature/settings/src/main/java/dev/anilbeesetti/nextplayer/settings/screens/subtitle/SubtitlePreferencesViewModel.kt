package dev.anilbeesetti.nextplayer.settings.screens.subtitle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.Font
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SubtitlePreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val preferencesFlow = preferencesRepository.playerPreferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = PlayerPreferences()
        )

    private val _uiState = MutableStateFlow(SubtitlePreferencesUIState())
    val uiState = _uiState.asStateFlow()

    fun onEvent(event: SubtitlePreferencesEvent) {
        if (event is SubtitlePreferencesEvent.ShowDialog) {
            _uiState.update {
                it.copy(showDialog = event.value)
            }
        }
    }

    fun updateSubtitleLanguage(value: String) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(preferredSubtitleLanguage = value)
            }
        }
    }

    fun updateSubtitleFont(value: Font) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(subtitleFont = value)
            }
        }
    }

    fun toggleSubtitleTextBold() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(subtitleTextBold = !it.subtitleTextBold)
            }
        }
    }

    fun updateSubtitleFontSize(value: Int) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(subtitleTextSize = value)
            }
        }
    }
}

data class SubtitlePreferencesUIState(
    val showDialog: SubtitlePreferenceDialog = SubtitlePreferenceDialog.None
)

sealed interface SubtitlePreferenceDialog {
    object SubtitleLanguageDialog : SubtitlePreferenceDialog
    object SubtitleFontDialog : SubtitlePreferenceDialog
    object SubtitleSizeDialog : SubtitlePreferenceDialog
    object None : SubtitlePreferenceDialog
}

sealed interface SubtitlePreferencesEvent {
    data class ShowDialog(val value: SubtitlePreferenceDialog) : SubtitlePreferencesEvent
}

fun SubtitlePreferencesViewModel.showDialog(dialog: SubtitlePreferenceDialog) {
    onEvent(SubtitlePreferencesEvent.ShowDialog(dialog))
}

fun SubtitlePreferencesViewModel.hideDialog() {
    onEvent(SubtitlePreferencesEvent.ShowDialog(SubtitlePreferenceDialog.None))
}
