package dev.anilbeesetti.nextplayer.core.model

sealed interface MediaItem {
    data class VideoItem(val video: Video) : MediaItem
    data class FolderItem(val folder: Folder) : MediaItem
}
