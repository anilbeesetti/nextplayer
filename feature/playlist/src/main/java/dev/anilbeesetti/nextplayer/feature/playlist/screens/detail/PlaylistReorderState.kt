package dev.anilbeesetti.nextplayer.feature.playlist.screens.detail

import dev.anilbeesetti.nextplayer.core.model.PlaylistItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class PlaylistMove(
    val uri: String,
    val toIndex: Int,
)

internal data class PlaylistReorderSnapshot(
    val displayedItems: List<PlaylistItem> = emptyList(),
    val isDragging: Boolean = false,
    val isMoving: Boolean = false,
)

internal class PlaylistReorderState {
    private val mutableState = MutableStateFlow(PlaylistReorderSnapshot())
    val state: StateFlow<PlaylistReorderSnapshot> = mutableState.asStateFlow()

    private var repositoryItems: List<PlaylistItem> = emptyList()
    private var pendingMove: PlaylistMove? = null

    fun updateRepositoryItems(items: List<PlaylistItem>) {
        repositoryItems = items
        val current = mutableState.value
        if (!current.isDragging && !current.isMoving) {
            mutableState.value = current.copy(displayedItems = items)
        }
    }

    fun startDrag(): Boolean {
        val current = mutableState.value
        if (current.isDragging || current.isMoving) return false
        pendingMove = null
        mutableState.value = current.copy(isDragging = true)
        return true
    }

    fun move(fromIndex: Int, toIndex: Int): Boolean {
        val current = mutableState.value
        if (!current.isDragging || current.isMoving) return false
        if (fromIndex !in current.displayedItems.indices || toIndex !in current.displayedItems.indices) return false

        val displayedItems = current.displayedItems.toMutableList()
        val moved = displayedItems.removeAt(fromIndex)
        displayedItems.add(toIndex, moved)
        pendingMove = PlaylistMove(moved.uriString, toIndex)
        mutableState.value = current.copy(displayedItems = displayedItems)
        return true
    }

    fun stopDragAndStartMove(): PlaylistMove? {
        val current = mutableState.value
        if (!current.isDragging || current.isMoving) return null
        val move = pendingMove
        pendingMove = null
        mutableState.value = if (move == null) {
            current.copy(displayedItems = repositoryItems, isDragging = false)
        } else {
            current.copy(isDragging = false, isMoving = true)
        }
        return move
    }

    fun startMove(uri: String, toIndex: Int): PlaylistMove? {
        val current = mutableState.value
        if (current.isDragging || current.isMoving) return null
        val move = PlaylistMove(uri, toIndex)
        mutableState.value = current.copy(isMoving = true)
        return move
    }

    fun finishMove() {
        pendingMove = null
        mutableState.value = PlaylistReorderSnapshot(displayedItems = repositoryItems)
    }
}
