package dev.anilbeesetti.nextplayer.feature.videopicker.screens

import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.Video

sealed interface VideosState {
    data object Loading : VideosState
    data class Success(val data: List<Video>) : VideosState {
        val recentPlayedVideo = data.recentPlayed()
    }
}

sealed interface FoldersState {
    data object Loading : FoldersState
    data class Success(val data: List<Folder>) : FoldersState {
        val recentPlayedVideo = data.flatMap { it.mediaList }.recentPlayed()
    }
}

private fun List<Video>.recentPlayed(): Video? =
    filter { it.lastPlayedAt != null }.sortedByDescending { it.lastPlayedAt?.time }.firstOrNull()
