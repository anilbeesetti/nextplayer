package com.graviton.settings.screens.thumbnail

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import com.graviton.core.data.repository.PreferencesRepository
import com.graviton.core.media.extensions.clearAllCache
import com.graviton.core.model.ApplicationPreferences
import com.graviton.core.model.ThumbnailGenerationStrategy
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ThumbnailPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val imageLoader: ImageLoader,
) : ViewModel() {

    private val uiStateInternal = MutableStateFlow(
        ThumbnailPreferencesUiState(
            preferences = preferencesRepository.applicationPreferences.value,
        ),
    )
    val uiState = uiStateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { preferences ->
                uiStateInternal.update { it.copy(preferences = preferences) }
            }
        }
    }

    fun onEvent(event: ThumbnailPreferencesEvent) {
        when (event) {
            is ThumbnailPreferencesEvent.UpdateStrategy -> updateStrategy(event.strategy)
            is ThumbnailPreferencesEvent.UpdateFramePosition -> updateFramePosition(event.position)
        }
    }

    private fun updateStrategy(strategy: ThumbnailGenerationStrategy) {
        viewModelScope.launch {
            val currentStrategy = uiState.value.preferences.thumbnailGenerationStrategy
            preferencesRepository.updateApplicationPreferences {
                it.copy(thumbnailGenerationStrategy = strategy)
            }
            // Clear cache only if strategy actually changed
            if (currentStrategy != strategy) {
                imageLoader.clearAllCache()
            }
        }
    }

    private fun updateFramePosition(position: Float) {
        viewModelScope.launch {
            val currentPosition = uiState.value.preferences.thumbnailFramePosition
            preferencesRepository.updateApplicationPreferences {
                it.copy(thumbnailFramePosition = position)
            }
            // Clear cache only if position actually changed
            if (currentPosition != position) {
                imageLoader.clearAllCache()
            }
        }
    }
}

@Stable
data class ThumbnailPreferencesUiState(
    val preferences: ApplicationPreferences = ApplicationPreferences(),
)

sealed interface ThumbnailPreferencesEvent {
    data class UpdateStrategy(val strategy: ThumbnailGenerationStrategy) : ThumbnailPreferencesEvent
    data class UpdateFramePosition(val position: Float) : ThumbnailPreferencesEvent
}
