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

/**
 * Owns the displayed order across drag, repository persistence, and observable repository handoff.
 *
 * Invariants:
 * 1. Drag or move ownership prevents repository emissions from replacing the displayed order.
 * 2. Persistence success records an emission-generation boundary; only a later emission may confirm
 *    or supersede that move and restore repository ownership.
 * 3. A handoff timeout releases move ownership to the expected persisted order, never to a snapshot
 *    captured before the success boundary. Any later idle emission remains authoritative.
 */
internal class PlaylistReorderState {
    private val mutableState = MutableStateFlow(PlaylistReorderSnapshot())
    val state: StateFlow<PlaylistReorderSnapshot> = mutableState.asStateFlow()

    private var repositoryItems: List<PlaylistItem> = emptyList()
    private var pendingMove: PlaylistMove? = null
    private var expectedRepositoryItems: List<PlaylistItem>? = null
    private var repositoryGeneration = 0L
    private var successGeneration: Long? = null

    fun updateRepositoryItems(items: List<PlaylistItem>): Boolean {
        repositoryGeneration += 1
        repositoryItems = items
        val current = mutableState.value
        if (!current.isDragging && !current.isMoving) {
            mutableState.value = current.copy(displayedItems = items)
            return false
        }
        val boundary = successGeneration
        if (current.isMoving && boundary != null && repositoryGeneration > boundary) {
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
            beginRepositoryHandoff(expectedItems = current.displayedItems)
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
        beginRepositoryHandoff(expectedItems = expectedItems)
        mutableState.value = current.copy(isMoving = true)
        return move
    }

    fun moveSucceeded(): Boolean {
        if (!mutableState.value.isMoving) return false
        successGeneration = repositoryGeneration
        return true
    }

    fun finishMove() {
        reconcileToRepository()
    }

    fun reconcileTimedOut() {
        val expectedItems = expectedRepositoryItems ?: return
        if (!mutableState.value.isMoving || successGeneration == null) return
        clearHandoff()
        mutableState.value = PlaylistReorderSnapshot(displayedItems = expectedItems)
    }

    private fun beginRepositoryHandoff(expectedItems: List<PlaylistItem>) {
        expectedRepositoryItems = expectedItems
        successGeneration = null
    }

    private fun reconcileToRepository() {
        clearHandoff()
        mutableState.value = PlaylistReorderSnapshot(displayedItems = repositoryItems)
    }

    private fun clearHandoff() {
        pendingMove = null
        expectedRepositoryItems = null
        successGeneration = null
    }
}
