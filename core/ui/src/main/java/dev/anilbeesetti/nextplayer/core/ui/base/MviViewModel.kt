package dev.anilbeesetti.nextplayer.core.ui.base

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

abstract class MviViewModel<State, Action> : ViewModel() {
    abstract val state: StateFlow<State>
    abstract fun onAction(action: Action)
}
