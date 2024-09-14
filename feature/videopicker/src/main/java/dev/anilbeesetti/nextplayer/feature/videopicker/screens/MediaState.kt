package dev.anilbeesetti.nextplayer.feature.videopicker.screens

import dev.anilbeesetti.nextplayer.core.model.Folder

sealed interface MediaState {
    data object Loading : MediaState
    data class Success(val data: Folder?) : MediaState
}
