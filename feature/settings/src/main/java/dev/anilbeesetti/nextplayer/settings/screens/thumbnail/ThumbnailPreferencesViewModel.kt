package dev.anilbeesetti.nextplayer.settings.screens.thumbnail

import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.media.extensions.clearAllCache
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.ThumbnailGenerationStrategy
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class ThumbnailPreferencesViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val imageLoader: ImageLoader,
    @InjectedParam internal var output: Output,
) : MviViewModel<ThumbnailPreferencesUiState, ThumbnailPreferencesEvent>() {

    data class Output(
        val navigateUp: () -> Unit,
    )

    override val state: StateFlow<ThumbnailPreferencesUiState> = preferencesRepository.applicationPreferences
        .map { ThumbnailPreferencesUiState(preferences = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000L),
            initialValue = ThumbnailPreferencesUiState(preferencesRepository.applicationPreferences.value),
        )

    override fun onAction(action: ThumbnailPreferencesEvent) {
        when (action) {
            is ThumbnailPreferencesEvent.NavigateUp -> output.navigateUp()

            is ThumbnailPreferencesEvent.UpdateStrategy -> updateStrategy(action.strategy)
            is ThumbnailPreferencesEvent.UpdateFramePosition -> updateFramePosition(action.position)
        }
    }

    private fun updateStrategy(strategy: ThumbnailGenerationStrategy) {
        viewModelScope.launch {
            val currentStrategy = preferencesRepository.applicationPreferences.value.thumbnailGenerationStrategy
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
            val currentPosition = preferencesRepository.applicationPreferences.value.thumbnailFramePosition
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
    data object NavigateUp : ThumbnailPreferencesEvent

    data class UpdateStrategy(val strategy: ThumbnailGenerationStrategy) : ThumbnailPreferencesEvent
    data class UpdateFramePosition(val position: Float) : ThumbnailPreferencesEvent
}
