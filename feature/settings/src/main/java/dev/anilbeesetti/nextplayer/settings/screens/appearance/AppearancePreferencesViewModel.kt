package dev.anilbeesetti.nextplayer.settings.screens.appearance

import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.ThemeConfig
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AppearancePreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : MviViewModel<AppearancePreferencesUiState, AppearancePreferencesEvent>() {

    private val uiStateInternal = MutableStateFlow(
        AppearancePreferencesUiState(
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

    override fun onAction(action: AppearancePreferencesEvent) {
        when (action) {
            is AppearancePreferencesEvent.ShowDialog -> showDialog(action.value)
            AppearancePreferencesEvent.ToggleDarkTheme -> toggleDarkTheme()
            is AppearancePreferencesEvent.UpdateThemeConfig -> updateThemeConfig(action.themeConfig)
            AppearancePreferencesEvent.ToggleUseDynamicColors -> toggleUseDynamicColors()
            AppearancePreferencesEvent.ToggleUseHighContrastDarkTheme -> toggleUseHighContrastDarkTheme()
        }
    }

    private fun showDialog(value: AppearancePreferenceDialog?) {
        uiStateInternal.update {
            it.copy(showDialog = value)
        }
    }

    private fun toggleDarkTheme() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(
                    themeConfig = if (it.themeConfig == ThemeConfig.ON) ThemeConfig.OFF else ThemeConfig.ON,
                )
            }
        }
    }

    private fun updateThemeConfig(themeConfig: ThemeConfig) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(themeConfig = themeConfig)
            }
        }
    }

    private fun toggleUseDynamicColors() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(useDynamicColors = !it.useDynamicColors)
            }
        }
    }

    private fun toggleUseHighContrastDarkTheme() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(useHighContrastDarkTheme = !it.useHighContrastDarkTheme)
            }
        }
    }
}

@Stable
data class AppearancePreferencesUiState(
    val showDialog: AppearancePreferenceDialog? = null,
    val preferences: ApplicationPreferences = ApplicationPreferences(),
)

sealed interface AppearancePreferencesEvent {
    data class ShowDialog(val value: AppearancePreferenceDialog?) : AppearancePreferencesEvent
    data object ToggleDarkTheme : AppearancePreferencesEvent
    data class UpdateThemeConfig(val themeConfig: ThemeConfig) : AppearancePreferencesEvent
    data object ToggleUseDynamicColors : AppearancePreferencesEvent
    data object ToggleUseHighContrastDarkTheme : AppearancePreferencesEvent
}

sealed interface AppearancePreferenceDialog {
    data object Theme : AppearancePreferenceDialog
}
