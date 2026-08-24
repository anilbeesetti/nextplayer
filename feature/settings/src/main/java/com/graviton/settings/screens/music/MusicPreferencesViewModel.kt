package com.graviton.settings.screens.music

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.graviton.core.data.repository.PreferencesRepository
import com.graviton.core.model.ApplicationPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MusicPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val uiStateInternal = MutableStateFlow(
        MusicPreferencesUiState(preferences = preferencesRepository.applicationPreferences.value),
    )
    val uiState = uiStateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { preferences ->
                uiStateInternal.update { it.copy(preferences = preferences) }
            }
        }
    }

    fun onEvent(event: MusicPreferencesEvent) {
        when (event) {
            MusicPreferencesEvent.ToggleShowLyrics -> update { it.copy(musicShowLyrics = !it.musicShowLyrics) }
            MusicPreferencesEvent.ToggleRememberShuffle -> update { it.copy(musicRememberShuffle = !it.musicRememberShuffle) }
            MusicPreferencesEvent.ClearHistory -> update {
                it.copy(musicRecentlyPlayedUris = emptyList(), musicFolderLastUri = emptyMap())
            }
        }
    }

    private fun update(transform: (ApplicationPreferences) -> ApplicationPreferences) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences(transform)
        }
    }
}

@Stable
data class MusicPreferencesUiState(
    val preferences: ApplicationPreferences = ApplicationPreferences(),
)

sealed interface MusicPreferencesEvent {
    data object ToggleShowLyrics : MusicPreferencesEvent
    data object ToggleRememberShuffle : MusicPreferencesEvent
    data object ClearHistory : MusicPreferencesEvent
}
