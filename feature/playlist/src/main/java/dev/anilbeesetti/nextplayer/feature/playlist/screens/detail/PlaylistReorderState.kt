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
    private var expectedRepositoryOrder: List<String>? = null
    private var repositoryOrderAtMoveStart: List<String>? = null
    private var isAwaitingRepository = false

    fun updateRepositoryItems(items: List<PlaylistItem>): Boolean {
        repositoryItems = items
        val current = mutableState.value
        if (!current.isDragging && !current.isMoving) {
            mutableState.value = current.copy(displayedItems = items)
            return false
        }
        if (current.isMoving && isAwaitingRepository && repositoryConfirmsOrDiverges()) {
            reconcileToRepository()
            return true
        }
        return false
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
            beginRepositoryHandoff(expectedOrder = current.displayedItems.map(PlaylistItem::uriString))
            current.copy(isDragging = false, isMoving = true)
        }
        return move
    }

    fun startMove(uri: String, toIndex: Int): PlaylistMove? {
        val current = mutableState.value
        if (current.isDragging || current.isMoving) return null
        val currentIndex = current.displayedItems.indexOfFirst { it.uriString == uri }
        if (currentIndex == -1 || current.displayedItems.isEmpty()) return null
        val targetIndex = toIndex.coerceIn(current.displayedItems.indices)
        if (currentIndex == targetIndex) return null
        val expectedItems = current.displayedItems.toMutableList()
        expectedItems.add(targetIndex, expectedItems.removeAt(currentIndex))
        val move = PlaylistMove(uri, targetIndex)
        beginRepositoryHandoff(expectedOrder = expectedItems.map(PlaylistItem::uriString))
        mutableState.value = current.copy(isMoving = true)
        return move
    }

    fun moveSucceeded(): Boolean {
        if (!mutableState.value.isMoving) return false
        isAwaitingRepository = true
        if (repositoryConfirmsOrDiverges()) {
            reconcileToRepository()
            return false
        }
        return true
    }

    fun finishMove() {
        reconcileToRepository()
    }

    fun reconcileTimedOut() {
        if (mutableState.value.isMoving && isAwaitingRepository) {
            reconcileToRepository()
        }
    }

    private fun beginRepositoryHandoff(expectedOrder: List<String>) {
        expectedRepositoryOrder = expectedOrder
        repositoryOrderAtMoveStart = repositoryItems.map(PlaylistItem::uriString)
        isAwaitingRepository = false
    }

    private fun repositoryConfirmsOrDiverges(): Boolean {
        val repositoryOrder = repositoryItems.map(PlaylistItem::uriString)
        return repositoryOrder == expectedRepositoryOrder || repositoryOrder != repositoryOrderAtMoveStart
    }

    private fun reconcileToRepository() {
        pendingMove = null
        expectedRepositoryOrder = null
        repositoryOrderAtMoveStart = null
        isAwaitingRepository = false
        mutableState.value = PlaylistReorderSnapshot(displayedItems = repositoryItems)
    }
}
