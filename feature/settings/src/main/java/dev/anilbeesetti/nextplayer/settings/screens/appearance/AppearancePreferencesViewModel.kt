package dev.anilbeesetti.nextplayer.settings.screens.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.ThemeConfig
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AppearancePreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {
    val preferencesFlow = preferencesRepository.applicationPreferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ApplicationPreferences(),
        )

    private val _uiState = MutableStateFlow(AppearancePreferencesUiState())
    val uiState = _uiState.asStateFlow()

    fun onEvent(event: AppearancePreferencesEvent) {
        if (event is AppearancePreferencesEvent.ShowDialog) {
            _uiState.update {
                it.copy(showDialog = event.value)
            }
        }
    }

    fun toggleDarkTheme() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(
                    themeConfig = if (it.themeConfig == ThemeConfig.ON) ThemeConfig.OFF else ThemeConfig.ON,
                )
            }
        }
    }

    fun updateThemeConfig(themeConfig: ThemeConfig) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(themeConfig = themeConfig)
            }
        }
    }

    fun toggleUseDynamicColors() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(useDynamicColors = !it.useDynamicColors)
            }
        }
    }

    fun toggleUseHighContrastDarkTheme() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(useHighContrastDarkTheme = !it.useHighContrastDarkTheme)
            }
        }
    }
}

data class AppearancePreferencesUiState(
    val showDialog: AppearancePreferenceDialog? = null,
)

sealed interface AppearancePreferencesEvent {
    data class ShowDialog(val value: AppearancePreferenceDialog?) : AppearancePreferencesEvent
}

sealed interface AppearancePreferenceDialog {
    object Theme : AppearancePreferenceDialog
}

fun AppearancePreferencesViewModel.showDialog(dialog: AppearancePreferenceDialog) {
    onEvent(AppearancePreferencesEvent.ShowDialog(dialog))
}

fun AppearancePreferencesViewModel.hideDialog() {
    onEvent(AppearancePreferencesEvent.ShowDialog(null))
}
