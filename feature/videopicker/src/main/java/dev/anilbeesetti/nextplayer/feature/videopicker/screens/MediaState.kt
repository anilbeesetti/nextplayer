package dev.anilbeesetti.nextplayer.feature.videopicker.screens

import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.Video

sealed interface VideosState {
    data object Loading : VideosState
    data class Success(val data: List<Video>) : VideosState {
        val recentPlayedVideo = data
            .filter { it.lastPlayedAt != null }
            .sortedByDescending { it.lastPlayedAt?.time }
            .firstOrNull()
    }
}

sealed interface FoldersState {
    data object Loading : FoldersState
    data class Success(val data: List<Folder>) : FoldersState
}
