package dev.anilbeesetti.nextplayer

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : MviViewModel<MainActivityUiState, Nothing>() {

    override val state = preferencesRepository.applicationPreferences.map { preferences ->
        MainActivityUiState.Success(preferences)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainActivityUiState.Loading,
    )

    // Application preferences are observed only; this view model has no input actions.
    override fun onAction(action: Nothing) = Unit
}

sealed interface MainActivityUiState {
    object Loading : MainActivityUiState
    data class Success(val preferences: ApplicationPreferences) : MainActivityUiState
}
