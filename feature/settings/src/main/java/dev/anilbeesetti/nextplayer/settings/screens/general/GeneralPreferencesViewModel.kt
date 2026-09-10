package dev.anilbeesetti.nextplayer.settings.screens.general

import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.media.extensions.clearAllCache
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class GeneralPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val imageLoader: ImageLoader,
) : MviViewModel<GeneralPreferencesUiState, GeneralPreferencesUiEvent>() {

    private val uiStateInternal = MutableStateFlow(GeneralPreferencesUiState())
    override val state = uiStateInternal.asStateFlow()

    override fun onAction(action: GeneralPreferencesUiEvent) {
        when (action) {
            is GeneralPreferencesUiEvent.ShowDialog -> showDialog(action.value)
            GeneralPreferencesUiEvent.ClearThumbnailCache -> clearThumbnailCache()
            GeneralPreferencesUiEvent.ResetSettings -> resetSettings()
        }
    }

    private fun showDialog(value: GeneralPreferencesDialog?) {
        uiStateInternal.value = uiStateInternal.value.copy(showDialog = value)
    }

    private fun clearThumbnailCache() {
        viewModelScope.launch {
            imageLoader.clearAllCache()
        }
    }

    private fun resetSettings() {
        viewModelScope.launch {
            preferencesRepository.resetPreferences()
        }
    }
}

data class GeneralPreferencesUiState(
    val showDialog: GeneralPreferencesDialog? = null,
)

sealed interface GeneralPreferencesDialog {
    data object ClearThumbnailCacheDialog : GeneralPreferencesDialog
    data object ResetSettingsDialog : GeneralPreferencesDialog
}

sealed interface GeneralPreferencesUiEvent {
    data class ShowDialog(val value: GeneralPreferencesDialog?) : GeneralPreferencesUiEvent
    data object ClearThumbnailCache : GeneralPreferencesUiEvent
    data object ResetSettings : GeneralPreferencesUiEvent
}
