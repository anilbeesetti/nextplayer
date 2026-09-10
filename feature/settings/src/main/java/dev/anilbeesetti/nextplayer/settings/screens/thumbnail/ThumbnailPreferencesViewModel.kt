package dev.anilbeesetti.nextplayer.settings.screens.thumbnail

import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.media.extensions.clearAllCache
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.ThumbnailGenerationStrategy
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ThumbnailPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val imageLoader: ImageLoader,
) : MviViewModel<ThumbnailPreferencesUiState, ThumbnailPreferencesEvent>() {

    private val uiStateInternal = MutableStateFlow(
        ThumbnailPreferencesUiState(
            preferences = preferencesRepository.applicationPreferences.value,
        ),
    )
    override val state = uiStateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { preferences ->
                uiStateInternal.update { it.copy(preferences = preferences) }
            }
        }
    }

    override fun onAction(action: ThumbnailPreferencesEvent) {
        when (action) {
            is ThumbnailPreferencesEvent.UpdateStrategy -> updateStrategy(action.strategy)
            is ThumbnailPreferencesEvent.UpdateFramePosition -> updateFramePosition(action.position)
        }
    }

    private fun updateStrategy(strategy: ThumbnailGenerationStrategy) {
        viewModelScope.launch {
            val currentStrategy = state.value.preferences.thumbnailGenerationStrategy
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
            val currentPosition = state.value.preferences.thumbnailFramePosition
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
