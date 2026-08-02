package dev.anilbeesetti.nextplayer.core.ui.base

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow


abstract class MviViewModel<State, Action>: ViewModel() {
    abstract val state: StateFlow<State>
    abstract fun onAction(action: Action)
}

sealed class DataState<out T : Any?> {
    data object Loading : DataState<Nothing>()
    data class Success<T : Any?>(val value: T) : DataState<T>()
    data class Error(val value: Throwable) : DataState<Nothing>()

    val result: T? get() = (this as? Success)?.value
    val error: Throwable? get() = (this as? Error)?.value
    val isLoading: Boolean get() = this is Loading
    val isError: Boolean get() = this is Error
}

sealed class ActionState {
    data object Idle : ActionState()
    data object Running : ActionState()
    data object Success : ActionState()
    data class Failed(val value: Throwable) : ActionState()

    val isSuccess: Boolean get() = this is Success
    val isRunning: Boolean get() = this is Running
    val isFailed: Boolean get() = this is Failed

    val error: Throwable? get() = (this as? Failed)?.value
    val errorMessage: String? get() = error?.message
}
