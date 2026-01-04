package dev.anilbeesetti.nextplayer.core.ui.base

sealed class ScreenState {
    data object Loading : ScreenState()
    data object Success : ScreenState()
    data class Error(val error: Throwable) : ScreenState()

    val isLoading: Boolean get() = this is Loading
}
