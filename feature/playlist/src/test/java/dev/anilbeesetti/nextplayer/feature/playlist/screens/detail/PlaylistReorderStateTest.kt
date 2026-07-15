package dev.anilbeesetti.nextplayer.feature.playlist.screens.detail

import dev.anilbeesetti.nextplayer.core.model.PlaylistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistReorderStateTest {
    private val reorderState = PlaylistReorderState()

    @Test
    fun failedMoveRollsDisplayedItemsBackToLatestRepositoryOrder() {
        val repositoryItems = items("one", "two", "three")
        reorderState.updateRepositoryItems(repositoryItems)

        assertTrue(reorderState.startDrag())
        assertTrue(reorderState.move(fromIndex = 2, toIndex = 0))
        assertEquals(listOf("three", "one", "two"), reorderState.state.value.displayedUris)
        assertEquals(
            PlaylistMove(uri = "content://three", toIndex = 0),
            reorderState.stopDragAndStartMove(),
        )

        reorderState.finishMove()

        assertEquals(repositoryItems, reorderState.state.value.displayedItems)
        assertFalse(reorderState.state.value.isDragging)
        assertFalse(reorderState.state.value.isMoving)
    }

    @Test
    fun repositoryEmissionsDoNotCancelPendingDragOrPersistedFinalMove() {
        reorderState.updateRepositoryItems(items("one", "two", "three"))
        assertTrue(reorderState.startDrag())
        assertTrue(reorderState.move(fromIndex = 0, toIndex = 1))

        reorderState.updateRepositoryItems(items("three", "two", "one"))

        assertEquals(listOf("two", "one", "three"), reorderState.state.value.displayedUris)
        assertTrue(reorderState.move(fromIndex = 1, toIndex = 2))
        assertEquals(
            PlaylistMove(uri = "content://one", toIndex = 2),
            reorderState.stopDragAndStartMove(),
        )

        reorderState.updateRepositoryItems(items("three", "one", "two"))
        assertEquals(listOf("two", "three", "one"), reorderState.state.value.displayedUris)
        assertTrue(reorderState.state.value.isMoving)

        reorderState.finishMove()

        assertEquals(listOf("three", "one", "two"), reorderState.state.value.displayedUris)
        assertFalse(reorderState.state.value.isMoving)
    }

    private val PlaylistReorderSnapshot.displayedUris: List<String>
        get() = displayedItems.map(PlaylistItem::uriString).map { it.removePrefix("content://") }
}

private fun items(vararg names: String): List<PlaylistItem> = names.mapIndexed { index, name ->
    PlaylistItem(
        uriString = "content://$name",
        title = name,
        position = index,
    )
}
