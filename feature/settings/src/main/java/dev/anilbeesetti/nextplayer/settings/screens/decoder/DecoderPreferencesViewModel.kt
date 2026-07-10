package dev.anilbeesetti.nextplayer.settings.screens.decoder

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
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
            DecoderPreferencesUiEvent.ToggleHwPlusAudioOnSwVideo -> toggleHwPlusAudioOnSwVideo()
        }
    }

    private fun toggleHwPlusAudioOnSwVideo() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(useHwPlusAudioOnSwVideo = !it.useHwPlusAudioOnSwVideo)
            }
        }
    }
}

@Stable
data class DecoderPreferencesUiState(
    val preferences: PlayerPreferences = PlayerPreferences(),
)

sealed interface DecoderPreferencesUiEvent {
    data object ToggleHwPlusAudioOnSwVideo : DecoderPreferencesUiEvent
}
