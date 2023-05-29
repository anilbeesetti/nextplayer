package dev.anilbeesetti.nextplayer.settings.screens.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.AppPrefs
import dev.anilbeesetti.nextplayer.core.model.ThemeConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppearancePreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {
    val preferencesFlow = preferencesRepository.appPrefsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppPrefs.default()
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
            preferencesRepository.setThemeConfig(
                if (preferencesFlow.value.themeConfig == ThemeConfig.DARK) {
                    ThemeConfig.LIGHT
                } else {
                    ThemeConfig.DARK
                }
            )
        }
    }

    fun updateThemeConfig(themeConfig: ThemeConfig) {
        viewModelScope.launch { preferencesRepository.setThemeConfig(themeConfig) }
    }

    fun toggleUseDynamicColors() {
        viewModelScope.launch {
            preferencesRepository.setUseDynamicColors(
                !preferencesFlow.value.useDynamicColors
            )
        }
    }
}

data class AppearancePreferencesUiState(
    val showDialog: AppearancePreferenceDialog = AppearancePreferenceDialog.None
)

sealed interface AppearancePreferencesEvent {
    data class ShowDialog(val value: AppearancePreferenceDialog) : AppearancePreferencesEvent
}

sealed interface AppearancePreferenceDialog {
    object Theme : AppearancePreferenceDialog
    object None : AppearancePreferenceDialog
}
