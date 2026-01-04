package dev.anilbeesetti.nextplayer.core.ui.base

sealed class DataState<out T : Any?> {
    data object Loading : DataState<Nothing>()
    data class Success<T : Any?>(val value: T) : DataState<T>()
    data class Error(val value: Throwable) : DataState<Nothing>()

    val result: T? get() = (this as? Success)?.value
    val error: Throwable? get() = (this as? Error)?.value
    val isLoading: Boolean get() = this is Loading
    val isError: Boolean get() = this is Error
}
