package com.graviton.settings.screens.music

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.graviton.core.data.repository.PreferencesRepository
import com.graviton.core.model.ApplicationPreferences
import com.graviton.core.model.MusicBackgroundStyle
import com.graviton.core.model.NowPlayingStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MusicPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {
    private val uiStateInternal = MutableStateFlow(MusicPreferencesUiState(preferencesRepository.applicationPreferences.value))
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
            MusicPreferencesEvent.ToggleDynamicBackground -> update { it.copy(musicDynamicArtworkBackground = !it.musicDynamicArtworkBackground) }
            MusicPreferencesEvent.ToggleGestures -> update { it.copy(musicGestureControls = !it.musicGestureControls) }
            MusicPreferencesEvent.ToggleMetadata -> update { it.copy(musicShowMetadata = !it.musicShowMetadata) }
            MusicPreferencesEvent.ToggleCodec -> update { it.copy(musicShowCodecInfo = !it.musicShowCodecInfo) }
            MusicPreferencesEvent.ToggleNextTrack -> update { it.copy(musicShowNextTrack = !it.musicShowNextTrack) }
            MusicPreferencesEvent.ToggleReplayGain -> update { it.copy(musicReplayGainEnabled = !it.musicReplayGainEnabled) }
            is MusicPreferencesEvent.SetReplayGainPreamp -> update { it.copy(musicReplayGainPreampDb = event.value.coerceIn(-15f, 15f)) }
            is MusicPreferencesEvent.SetSeekSensitivity -> update { it.copy(musicSeekGestureSensitivity = event.value.coerceIn(0.5f, 2f)) }
            MusicPreferencesEvent.ToggleEqualizer -> update { it.copy(musicEqualizerEnabled = !it.musicEqualizerEnabled) }
            MusicPreferencesEvent.ResetEqualizer -> update { it.copy(musicEqualizerGainsDb = List(15) { 0f }) }
            MusicPreferencesEvent.ToggleLyricsButton -> update { it.copy(musicShowLyricsButton = !it.musicShowLyricsButton) }
            MusicPreferencesEvent.ToggleQueueButton -> update { it.copy(musicShowQueueButton = !it.musicShowQueueButton) }
            MusicPreferencesEvent.ToggleSleepButton -> update { it.copy(musicShowSleepTimerButton = !it.musicShowSleepTimerButton) }
            MusicPreferencesEvent.ToggleAnimations -> update { it.copy(musicAnimationsEnabled = !it.musicAnimationsEnabled) }
            MusicPreferencesEvent.ClearHistory -> update { it.copy(musicRecentlyPlayedUris = emptyList(), musicFolderLastUri = emptyMap()) }
            is MusicPreferencesEvent.SetStyle -> update { it.copy(musicNowPlayingStyle = event.style) }
            is MusicPreferencesEvent.SetBackground -> update { it.copy(musicBackgroundStyle = event.style) }
            is MusicPreferencesEvent.SetArtworkRadius -> update { it.copy(musicArtworkCornerRadius = event.value.coerceIn(0f, 48f)) }
            is MusicPreferencesEvent.SetArtworkSize -> update { it.copy(musicArtworkSizePercent = event.value.coerceIn(70, 100)) }
            is MusicPreferencesEvent.SetBlur -> update { it.copy(musicBlurIntensity = event.value.coerceIn(0f, 48f)) }
            is MusicPreferencesEvent.SetEqualizerBand -> update { preferences ->
                val levels = preferences.musicEqualizerGainsDb.toMutableList().apply {
                    while (size < 15) add(0f)
                    this[event.index.coerceIn(0, 14)] = event.gainDb.coerceIn(-15f, 15f)
                }
                preferences.copy(musicEqualizerGainsDb = levels)
            }
        }
    }

    private fun update(transform: (ApplicationPreferences) -> ApplicationPreferences) {
        viewModelScope.launch { preferencesRepository.updateApplicationPreferences(transform) }
    }
}

@Stable
data class MusicPreferencesUiState(val preferences: ApplicationPreferences = ApplicationPreferences())

sealed interface MusicPreferencesEvent {
    data object ToggleShowLyrics : MusicPreferencesEvent
    data object ToggleRememberShuffle : MusicPreferencesEvent
    data object ToggleDynamicBackground : MusicPreferencesEvent
    data object ToggleGestures : MusicPreferencesEvent
    data object ToggleMetadata : MusicPreferencesEvent
    data object ToggleCodec : MusicPreferencesEvent
    data object ToggleNextTrack : MusicPreferencesEvent
    data object ToggleReplayGain : MusicPreferencesEvent
    data object ToggleEqualizer : MusicPreferencesEvent
    data object ResetEqualizer : MusicPreferencesEvent
    data object ToggleLyricsButton : MusicPreferencesEvent
    data object ToggleQueueButton : MusicPreferencesEvent
    data object ToggleSleepButton : MusicPreferencesEvent
    data object ToggleAnimations : MusicPreferencesEvent
    data object ClearHistory : MusicPreferencesEvent
    data class SetStyle(val style: NowPlayingStyle) : MusicPreferencesEvent
    data class SetBackground(val style: MusicBackgroundStyle) : MusicPreferencesEvent
    data class SetArtworkRadius(val value: Float) : MusicPreferencesEvent
    data class SetArtworkSize(val value: Int) : MusicPreferencesEvent
    data class SetBlur(val value: Float) : MusicPreferencesEvent
    data class SetReplayGainPreamp(val value: Float) : MusicPreferencesEvent
    data class SetSeekSensitivity(val value: Float) : MusicPreferencesEvent
    data class SetEqualizerBand(val index: Int, val gainDb: Float) : MusicPreferencesEvent
}
