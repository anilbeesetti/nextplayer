package dev.anilbeesetti.nextplayer.feature.videopicker.screens

import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.Video

sealed interface VideosState {
    data object Loading : VideosState
    data class Success(val data: List<Video>) : VideosState {
        val recentPlayedVideo = data.recentPlayed()
        val firstVideo = data.firstOrNull()
    }
}

sealed interface FoldersState {
    data object Loading : FoldersState
    data class Success(val data: List<Folder>) : FoldersState {
        private val media = data.flatMap { it.mediaList }
        val recentPlayedVideo = media.recentPlayed()
        val firstVideo = media.firstOrNull()
    }
}

private fun List<Video>.recentPlayed(): Video? =
    filter { it.lastPlayedAt != null }.sortedByDescending { it.lastPlayedAt?.time }.firstOrNull()
