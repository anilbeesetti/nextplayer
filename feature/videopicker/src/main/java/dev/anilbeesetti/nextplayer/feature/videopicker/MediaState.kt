package dev.anilbeesetti.nextplayer.feature.videopicker

sealed interface MediaState {
    object Loading : MediaState
    data class Success<T>(val data: List<T>) : MediaState
}