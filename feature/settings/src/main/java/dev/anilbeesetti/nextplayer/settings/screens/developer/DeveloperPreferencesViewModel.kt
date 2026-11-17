package dev.anilbeesetti.nextplayer.settings.screens.developer

import androidx.lifecycle.viewModelScope
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.ArtworkStyle
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class DeveloperPreferencesViewModel(
    private val preferencesRepository: PreferencesRepository,
    @InjectedParam internal var output: Output,
) : MviViewModel<PlayerPreferences, DeveloperPreferencesEvent>() {
    data class Output(val navigateUp: () -> Unit)

    override val state = preferencesRepository.playerPreferences

    override fun onAction(action: DeveloperPreferencesEvent) {
        when (action) {
            DeveloperPreferencesEvent.NavigateUp -> output.navigateUp()

            is DeveloperPreferencesEvent.ControlsArtwork -> viewModelScope.launch {
                preferencesRepository.updatePlayerPreferences {
                    it.copy(controlsArtworkStyle = action.style)
                }
            }

            is DeveloperPreferencesEvent.QueueArtwork -> viewModelScope.launch {
                preferencesRepository.updatePlayerPreferences {
                    it.copy(queueArtworkStyle = action.style)
                }
            }
        }
    }
}

sealed interface DeveloperPreferencesEvent {
    data object NavigateUp : DeveloperPreferencesEvent
    data class ControlsArtwork(val style: ArtworkStyle) : DeveloperPreferencesEvent
    data class QueueArtwork(val style: ArtworkStyle) : DeveloperPreferencesEvent
}
