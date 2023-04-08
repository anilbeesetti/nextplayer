package dev.anilbeesetti.nextplayer.settings.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.datastore.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.datastore.Resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val preferencesFlow = preferencesRepository.playerPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PlayerPreferences()
        )

    private val _uiState = MutableStateFlow(UIState())
    val uiState = _uiState.asStateFlow()

    fun onEvent(event: PlayerPreferencesEvent) {
        when (event) {
            is PlayerPreferencesEvent.ResumeDialog -> _uiState.update {
                it.copy(
                    showResumeDialog = event.value
                )
            }
        }
    }

    fun updateResume(resume: Resume) {
        viewModelScope.launch { preferencesRepository.setPlaybackResume(resume) }
    }

    fun toggleRememberBrightnessLevel() {
        viewModelScope.launch {
            preferencesRepository.shouldRememberPlayerBrightness(
                !preferencesFlow.value.rememberPlayerBrightness
            )
        }
    }
}

data class UIState(
    val showResumeDialog: Boolean = false
)

sealed interface PlayerPreferencesEvent {
    data class ResumeDialog(val value: Boolean) : PlayerPreferencesEvent
}
