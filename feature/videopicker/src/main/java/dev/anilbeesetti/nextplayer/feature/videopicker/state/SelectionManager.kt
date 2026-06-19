package dev.anilbeesetti.nextplayer.feature.videopicker.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.Video
import java.io.Serializable

@Composable
fun rememberSelectionManager(): SelectionManager {
    return rememberSaveable(saver = SelectionManager.Saver) {
        SelectionManager()
    }
}

@Stable
class SelectionManager(
    initialSelectionItems: Set<SelectionItem> = emptySet(),
    initialIsInSelectionMode: Boolean = false,
) {
    var selectionItems: Set<SelectionItem> by mutableStateOf(initialSelectionItems)
        private set

    var isInSelectionMode: Boolean by mutableStateOf(initialIsInSelectionMode)
        private set

    val isSingleVideoSelected: Boolean by derivedStateOf { selectionItems.size == 1 && selectionItems.first() is SelectionItem.Video }

    fun toggleFolderSelection(folder: Folder) {
        val selectedFolder = selectionItems.find { it.id == folder.path }
        selectionItems = if (selectedFolder != null) {
            selectionItems - selectedFolder
        } else {
            selectionItems + folder.toSelectedFolder()
        }
        if (selectionItems.isNotEmpty()) {
            enterSelectionMode()
        } else {
            exitSelectionMode()
        }
    }

    fun toggleVideoSelection(video: Video) {
        val selectedVideo = selectionItems.find { it.id == video.uriString }
        selectionItems = if (selectedVideo != null) {
            selectionItems - selectedVideo
        } else {
            selectionItems + video.toSelectedVideo()
        }
        if (selectionItems.isNotEmpty()) {
            enterSelectionMode()
        } else {
            exitSelectionMode()
        }
    }

    fun selectFolder(folder: Folder) {
        enterSelectionMode()
        selectionItems = selectionItems + folder.toSelectedFolder()
    }

    fun selectVideo(video: Video) {
        enterSelectionMode()
        selectionItems = selectionItems + video.toSelectedVideo()
    }

    fun clearSelection() {
        selectionItems = emptySet()
    }

    fun enterSelectionMode() {
        isInSelectionMode = true
    }

    fun exitSelectionMode() {
        isInSelectionMode = false
        selectionItems = emptySet()
    }

    fun isFolderSelected(folder: Folder): Boolean {
        return selectionItems.find { it.id == folder.path } != null
    }

    fun isVideoSelected(video: Video): Boolean {
        return selectionItems.find { it.id == video.uriString } != null
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        val Saver = Saver<SelectionManager, Map<String, Any>>(
            save = {
                mapOf(
                    "selectionItems" to it.selectionItems,
                    "isInSelectionMode" to it.isInSelectionMode,
                )
            },
            restore = {
                SelectionManager(
                    initialSelectionItems = (it["selectionItems"] as? Set<SelectionItem>) ?: emptySet(),
                    initialIsInSelectionMode = it["isInSelectionMode"] as? Boolean ?: false,
                )
            },
        )
    }
}

sealed interface SelectionItem: Serializable {
    @Stable
    data class Folder(
        override val name: String,
        val path: String,
    ) : SelectionItem {
        override val id: String = path
    }

    @Stable
    data class Video(
        override val name: String,
        val uriString: String,
    ) : SelectionItem {
        override val id: String = uriString
    }

    val id: String
    val name: String
}

private fun Folder.toSelectedFolder() = SelectionItem.Folder(
    name = name,
    path = path,
)

private fun Video.toSelectedVideo() = SelectionItem.Video(
    name = displayName,
    uriString = uriString,
)
