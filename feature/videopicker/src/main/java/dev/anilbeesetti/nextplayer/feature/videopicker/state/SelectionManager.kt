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
    initialSelectedVideos: Set<SelectedVideo> = emptySet(),
    initialSelectedFolders: Set<SelectedFolder> = emptySet(),
) {
    var selectedVideos: Set<SelectedVideo> by mutableStateOf(initialSelectedVideos)
        private set

    var selectedFolders: Set<SelectedFolder> by mutableStateOf(initialSelectedFolders)
        private set

    val allSelectedVideos: Set<SelectedVideo> by derivedStateOf { selectedVideos + selectedFolders.flatMap { it.mediaList } }

    val isInSelectionMode: Boolean by derivedStateOf { selectedVideos.isNotEmpty() || selectedFolders.isNotEmpty() }

    val isSingleVideoSelected: Boolean by derivedStateOf { selectedVideos.size == 1 && selectedFolders.isEmpty() }

    fun toggleFolderSelection(folder: Folder) {
        val selectedFolder = selectedFolders.find { it.path == folder.path }
        selectedFolders = if (selectedFolder != null) {
            selectedFolders - selectedFolder
        } else {
            selectedFolders + folder.toSelectedFolder()
        }
    }

    fun toggleVideoSelection(video: Video) {
        val selectedVideo = selectedVideos.find { it.uriString == video.uriString }
        selectedVideos = if (selectedVideo != null) {
            selectedVideos - selectedVideo
        } else {
            selectedVideos + video.toSelectedVideo()
        }
    }

    fun clearSelection() {
        selectedVideos = emptySet()
        selectedFolders = emptySet()
    }

    fun isFolderSelected(folder: Folder): Boolean {
        return selectedFolders.find { it.path == folder.path } != null
    }

    fun isVideoSelected(video: Video): Boolean {
        return selectedVideos.find { it.uriString == video.uriString } != null
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        val Saver = Saver<SelectionManager, Map<String, Set<Serializable>>>(
            save = {
                mapOf(
                    "selectedVideos" to it.selectedVideos,
                    "selectedFolders" to it.selectedFolders,
                )
            },
            restore = {
                SelectionManager(
                    initialSelectedVideos = (it["selectedVideos"] as? Set<SelectedVideo>)
                        ?: emptySet(),
                    initialSelectedFolders = (it["selectedFolders"] as? Set<SelectedFolder>)
                        ?: emptySet(),
                )
            },
        )
    }
}

@Stable
data class SelectedFolder(
    val name: String,
    val path: String,
    val mediaList: List<SelectedVideo>,
) : Serializable

@Stable
data class SelectedVideo(
    val name: String,
    val nameWithExtension: String,
    val uriString: String,
) : Serializable

private fun Folder.toSelectedFolder() = SelectedFolder(
    name = name,
    path = path,
    mediaList = mediaList.map { it.toSelectedVideo() },
)

private fun Video.toSelectedVideo() = SelectedVideo(
    name = displayName,
    nameWithExtension = nameWithExtension,
    uriString = uriString,
)
