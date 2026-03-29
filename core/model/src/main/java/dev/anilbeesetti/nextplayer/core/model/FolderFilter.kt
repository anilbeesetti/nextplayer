package dev.anilbeesetti.nextplayer.core.model

/**
 * Defines filtering criteria for querying media from folders.
 */
sealed class FolderFilter {
    /**
     * Returns all media across all folders without any path filtering.
     */
    data object All : FolderFilter()

    /**
     * Returns media from a specific folder path.
     *
     * @param folderPath The absolute path to the folder to filter by.
     * @param directChildrenOnly When true, only returns media directly in this folder.
     *                           When false, includes all descendants recursively.
     */
    data class WithPath(
        val folderPath: String,
        val directChildrenOnly: Boolean = true,
    ) : FolderFilter()
}
