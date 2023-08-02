package dev.anilbeesetti.nextplayer.feature.videopicker.screens

import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.Video

sealed interface VideosState {
    object Loading : VideosState
    data class Success(val data: List<Video>) : VideosState
}

sealed interface FoldersState {
    object Loading : FoldersState
    data class Success(val data: List<Folder>) : FoldersState
}
