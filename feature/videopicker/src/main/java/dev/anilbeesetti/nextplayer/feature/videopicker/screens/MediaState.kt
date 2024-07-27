package dev.anilbeesetti.nextplayer.feature.videopicker.screens

import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.model.recentPlayed

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

sealed interface FolderTreeState {
    data object Loading : FolderTreeState
    data class Success(val data: Folder) : FolderTreeState
}

sealed interface MediaState {
    data object Loading : MediaState
    data class Success(val data: Folder?) : MediaState
}
