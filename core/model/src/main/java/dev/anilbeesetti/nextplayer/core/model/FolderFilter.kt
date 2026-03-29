package dev.anilbeesetti.nextplayer.core.model

sealed class FolderFilter {
    data object All : FolderFilter()
    data class WithPath(
        val folderPath: String,
        val directChildrenOnly: Boolean = true,
    ) : FolderFilter()
}
