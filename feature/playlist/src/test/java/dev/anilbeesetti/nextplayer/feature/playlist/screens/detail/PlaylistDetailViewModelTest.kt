package dev.anilbeesetti.nextplayer.feature.playlist.screens.detail

import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRefreshResult
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import dev.anilbeesetti.nextplayer.core.model.Playlist
import dev.anilbeesetti.nextplayer.core.model.PlaylistItem
import dev.anilbeesetti.nextplayer.core.model.PlaylistItemInput
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeDetailPlaylistRepository
    private lateinit var viewModel: PlaylistDetailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeDetailPlaylistRepository()
        viewModel = PlaylistDetailViewModel(playlistId = 42, repository = repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun repositoryEmissionStopsLoadingAndPublishesPlaylist() = runTest(dispatcher) {
        collectUiState()
        val playlist = playlist(items = listOf(item("content://1", 0)))

        repository.playlist.value = playlist
        runCurrent()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(playlist, viewModel.uiState.value.playlist)
    }

    @Test
    fun playAllEmitsPlaylistIdAndFirstStart() = runTest(dispatcher) {
        collectUiState()
        repository.playlist.value = playlist(
            items = listOf(item("content://1", 0), item("content://2", 1)),
        )
        runCurrent()
        val event = async { viewModel.events.first() }

        viewModel.onAction(PlaylistDetailAction.PlayAll)

        assertEquals(
            PlaylistDetailEvent.Play(
                playlistId = 42,
                startUri = "content://1",
            ),
            event.await(),
        )
    }

    @Test
    fun playItemEmitsPlaylistIdAndSelectedStart() = runTest(dispatcher) {
        collectUiState()
        repository.playlist.value = playlist(
            items = listOf(item("content://1", 0), item("content://2", 1)),
        )
        runCurrent()
        val event = async { viewModel.events.first() }

        viewModel.onAction(PlaylistDetailAction.PlayItem("content://2"))

        assertEquals(
            PlaylistDetailEvent.Play(playlistId = 42, startUri = "content://2"),
            event.await(),
        )
    }

    @Test
    fun emptyAndNotFoundPlaybackDoNotEmitEvents() = runTest(dispatcher) {
        collectUiState()
        val events = mutableListOf<PlaylistDetailEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.toList(events)
        }
        repository.playlist.value = playlist(items = emptyList())
        runCurrent()

        viewModel.onAction(PlaylistDetailAction.PlayAll)
        viewModel.onAction(PlaylistDetailAction.PlayItem("content://missing"))
        runCurrent()

        assertEquals(emptyList<PlaylistDetailEvent>(), events)
    }

    @Test
    fun playbackDoesNotEmitWhileDragging() = runTest(dispatcher) {
        collectUiState()
        repository.playlist.value = playlist(
            items = listOf(item("content://1", 0), item("content://2", 1), item("content://3", 2)),
        )
        runCurrent()
        val events = mutableListOf<PlaylistDetailEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.toList(events)
        }

        viewModel.onAction(PlaylistDetailAction.StartMoveDrag)
        viewModel.onAction(PlaylistDetailAction.PreviewMove(fromIndex = 0, toIndex = 1))
        viewModel.onAction(PlaylistDetailAction.PlayItem("content://1"))
        viewModel.onAction(PlaylistDetailAction.PlayAll)
        runCurrent()

        assertEquals(emptyList<PlaylistDetailEvent>(), events)
    }

    @Test
    fun playbackDoesNotEmitWhileMoving() = runTest(dispatcher) {
        collectUiState()
        repository.playlist.value = playlist(
            items = listOf(item("content://1", 0), item("content://2", 1), item("content://3", 2)),
        )
        runCurrent()
        repository.moveGate = CompletableDeferred()
        val events = mutableListOf<PlaylistDetailEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.toList(events)
        }

        viewModel.onAction(PlaylistDetailAction.MoveItem("content://1", 2))
        runCurrent()
        assertTrue(viewModel.uiState.value.isMoving)

        viewModel.onAction(PlaylistDetailAction.PlayAll)
        viewModel.onAction(PlaylistDetailAction.PlayItem("content://1"))
        runCurrent()

        assertEquals(emptyList<PlaylistDetailEvent>(), events)

        repository.moveGate?.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun refreshIsRejectedForEditablePlaylist() = runTest(dispatcher) {
        collectUiState()
        repository.playlist.value = playlist(type = PlaylistType.EDITABLE)
        runCurrent()
        val event = async { viewModel.events.first() }

        viewModel.onAction(PlaylistDetailAction.Refresh)
        advanceUntilIdle()

        assertEquals(PlaylistDetailEvent.Message(EDITABLE_REFRESH_MESSAGE), event.await())
        assertEquals(emptyList<Long>(), repository.refreshedIds)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun linkedRefreshKeepsCachedItemsVisibleAndResetsProgressOnFailure() = runTest(dispatcher) {
        collectUiState()
        val cached = listOf(item("content://cached", 0))
        repository.playlist.value = playlist(type = PlaylistType.M3U_URL, items = cached)
        repository.refreshGate = CompletableDeferred()
        repository.refreshFailure = IOException("Source unavailable")
        runCurrent()
        val event = async { viewModel.events.first() }

        viewModel.onAction(PlaylistDetailAction.Refresh)
        runCurrent()

        assertTrue(viewModel.uiState.value.isRefreshing)
        assertEquals(cached, viewModel.uiState.value.playlist?.items)

        repository.refreshGate?.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(cached, viewModel.uiState.value.playlist?.items)
        assertEquals(PlaylistDetailEvent.Message("Source unavailable"), event.await())
    }

    @Test
    fun linkedRefreshResetsProgressWithoutEventCollector() = runTest(dispatcher) {
        collectUiState()
        repository.playlist.value = playlist(type = PlaylistType.M3U_URL)
        repository.refreshFailure = IOException("Source unavailable")
        runCurrent()

        viewModel.onAction(PlaylistDetailAction.Refresh)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(
            PlaylistDetailEvent.Message("Source unavailable"),
            viewModel.events.first(),
        )
    }

    @Test
    fun concurrentRefreshRequestsMakeOneRepositoryCall() = runTest(dispatcher) {
        collectUiState()
        repository.playlist.value = playlist(type = PlaylistType.M3U_URL)
        repository.refreshGate = CompletableDeferred()
        runCurrent()

        viewModel.onAction(PlaylistDetailAction.Refresh)
        viewModel.onAction(PlaylistDetailAction.Refresh)
        runCurrent()

        assertEquals(listOf(42L), repository.refreshedIds)

        repository.refreshGate?.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun linkedRefreshReportsItemAndSkippedCounts() = runTest(dispatcher) {
        collectUiState()
        repository.playlist.value = playlist(type = PlaylistType.M3U_FILE)
        repository.refreshResult = PlaylistRefreshResult(42, itemCount = 3, skippedEntries = 1)
        runCurrent()
        val event = async { viewModel.events.first() }

        viewModel.onAction(PlaylistDetailAction.Refresh)
        advanceUntilIdle()

        assertEquals(PlaylistDetailEvent.Message("Loaded 3 items; skipped 1 invalid entry."), event.await())
        assertEquals(listOf(42L), repository.refreshedIds)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun linkedPlaylistCannotMoveItems() = runTest(dispatcher) {
        collectUiState()
        repository.playlist.value = playlist(type = PlaylistType.M3U_URL)
        runCurrent()
        val event = async { viewModel.events.first() }

        viewModel.onAction(PlaylistDetailAction.MoveItem("content://1", 1))
        advanceUntilIdle()

        assertEquals(PlaylistDetailEvent.Message(LINKED_MOVE_MESSAGE), event.await())
        assertEquals(emptyList<MoveCall>(), repository.moveCalls)
    }

    @Test
    fun editableMoveDelegatesUriAndDestination() = runTest(dispatcher) {
        collectUiState()
        repository.playlist.value = playlist(
            type = PlaylistType.EDITABLE,
            items = listOf(item("content://1", 0), item("content://2", 1)),
        )
        runCurrent()

        viewModel.onAction(PlaylistDetailAction.MoveItem("content://2", 0))
        advanceUntilIdle()

        assertEquals(listOf(MoveCall(42, "content://2", 0)), repository.moveCalls)
    }

    @Test
    fun failedOptimisticMoveRollsDisplayedItemsBackToRepositoryOrder() = runTest(dispatcher) {
        collectUiState()
        val repositoryItems = listOf(
            item("content://1", 0),
            item("content://2", 1),
            item("content://3", 2),
        )
        repository.playlist.value = playlist(items = repositoryItems)
        repository.moveGate = CompletableDeferred()
        repository.moveFailure = IOException("Move failed")
        runCurrent()

        viewModel.onAction(PlaylistDetailAction.StartMoveDrag)
        viewModel.onAction(PlaylistDetailAction.PreviewMove(fromIndex = 2, toIndex = 0))
        viewModel.onAction(PlaylistDetailAction.StopMoveDrag)
        runCurrent()

        assertTrue(viewModel.uiState.value.isMoving)
        assertEquals(
            listOf("content://3", "content://1", "content://2"),
            viewModel.uiState.value.playlist?.items?.map(PlaylistItem::uriString),
        )

        repository.moveGate?.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isMoving)
        assertEquals(repositoryItems, viewModel.uiState.value.playlist?.items)
    }

    @Test
    fun successfulMoveKeepsOptimisticOrderAndOwnershipUntilMatchingRepositoryEmission() = runTest(dispatcher) {
        collectUiState()
        repository.playlist.value = playlist(
            items = listOf(item("content://1", 0), item("content://2", 1), item("content://3", 2)),
        )
        runCurrent()

        viewModel.onAction(PlaylistDetailAction.StartMoveDrag)
        viewModel.onAction(PlaylistDetailAction.PreviewMove(fromIndex = 2, toIndex = 0))
        viewModel.onAction(PlaylistDetailAction.StopMoveDrag)
        runCurrent()

        assertTrue(viewModel.uiState.value.isMoving)
        assertEquals(
            listOf("content://3", "content://1", "content://2"),
            viewModel.uiState.value.playlist?.items?.map(PlaylistItem::uriString),
        )

        viewModel.onAction(PlaylistDetailAction.MoveItem("content://1", 2))
        runCurrent()
        assertEquals(listOf(MoveCall(42, "content://3", 0)), repository.moveCalls)

        repository.playlist.value = playlist(
            items = listOf(item("content://3", 0), item("content://1", 1), item("content://2", 2)),
        )
        runCurrent()

        assertFalse(viewModel.uiState.value.isMoving)
        assertEquals(
            listOf("content://3", "content://1", "content://2"),
            viewModel.uiState.value.playlist?.items?.map(PlaylistItem::uriString),
        )
    }

    @Test
    fun divergentRepositoryEmissionReleasesSuccessfulMoveToRepositoryOrder() = runTest(dispatcher) {
        collectUiState()
        repository.playlist.value = playlist(
            items = listOf(item("content://1", 0), item("content://2", 1), item("content://3", 2)),
        )
        runCurrent()

        viewModel.onAction(PlaylistDetailAction.StartMoveDrag)
        viewModel.onAction(PlaylistDetailAction.PreviewMove(fromIndex = 2, toIndex = 0))
        viewModel.onAction(PlaylistDetailAction.StopMoveDrag)
        runCurrent()

        repository.playlist.value = playlist(
            items = listOf(item("content://2", 0), item("content://1", 1), item("content://3", 2)),
        )
        runCurrent()

        assertFalse(viewModel.uiState.value.isMoving)
        assertEquals(
            listOf("content://2", "content://1", "content://3"),
            viewModel.uiState.value.playlist?.items?.map(PlaylistItem::uriString),
        )
    }

    @Test
    fun timedOutSuccessfulMoveKeepsExpectedOrderUntilLateRepositoryEmission() = runTest(dispatcher) {
        collectUiState()
        val initialItems = listOf(
            item("content://1", 0),
            item("content://2", 1),
            item("content://3", 2),
        )
        repository.playlist.value = playlist(items = initialItems)
        runCurrent()

        viewModel.onAction(PlaylistDetailAction.StartMoveDrag)
        viewModel.onAction(PlaylistDetailAction.PreviewMove(fromIndex = 2, toIndex = 0))
        viewModel.onAction(PlaylistDetailAction.StopMoveDrag)
        runCurrent()

        assertTrue(viewModel.uiState.value.isMoving)

        advanceTimeBy(2_000)
        runCurrent()

        assertFalse(viewModel.uiState.value.isMoving)
        assertEquals(
            listOf("content://3", "content://1", "content://2"),
            viewModel.uiState.value.playlist?.items?.map(PlaylistItem::uriString),
        )

        repository.playlist.value = playlist(
            items = listOf(item("content://2", 0), item("content://3", 1), item("content://1", 2)),
        )
        runCurrent()

        assertEquals(
            listOf("content://2", "content://3", "content://1"),
            viewModel.uiState.value.playlist?.items?.map(PlaylistItem::uriString),
        )
    }

    @Test
    fun emissionDuringPersistenceCannotConfirmUntilLaterPostSuccessEmission() = runTest(dispatcher) {
        collectUiState()
        repository.playlist.value = playlist(
            items = listOf(item("content://1", 0), item("content://2", 1), item("content://3", 2)),
        )
        repository.moveGate = CompletableDeferred()
        runCurrent()

        viewModel.onAction(PlaylistDetailAction.StartMoveDrag)
        viewModel.onAction(PlaylistDetailAction.PreviewMove(fromIndex = 2, toIndex = 0))
        viewModel.onAction(PlaylistDetailAction.StopMoveDrag)
        runCurrent()

        repository.playlist.value = playlist(
            items = listOf(item("content://2", 0), item("content://1", 1), item("content://3", 2)),
        )
        runCurrent()
        assertTrue(viewModel.uiState.value.isMoving)

        repository.moveGate?.complete(Unit)
        runCurrent()

        assertTrue(viewModel.uiState.value.isMoving)
        assertEquals(
            listOf("content://3", "content://1", "content://2"),
            viewModel.uiState.value.playlist?.items?.map(PlaylistItem::uriString),
        )

        repository.playlist.value = playlist(
            items = listOf(item("content://3", 0), item("content://2", 1), item("content://1", 2)),
        )
        runCurrent()

        assertFalse(viewModel.uiState.value.isMoving)
        assertEquals(
            listOf("content://3", "content://2", "content://1"),
            viewModel.uiState.value.playlist?.items?.map(PlaylistItem::uriString),
        )
    }

    @Test
    fun repositoryEmissionDuringPendingDragKeepsFinalUriAndIndex() = runTest(dispatcher) {
        collectUiState()
        repository.playlist.value = playlist(
            items = listOf(item("content://1", 0), item("content://2", 1), item("content://3", 2)),
        )
        repository.moveGate = CompletableDeferred()
        runCurrent()

        viewModel.onAction(PlaylistDetailAction.StartMoveDrag)
        viewModel.onAction(PlaylistDetailAction.PreviewMove(fromIndex = 0, toIndex = 1))
        repository.playlist.value = playlist(
            items = listOf(item("content://3", 0), item("content://2", 1), item("content://1", 2)),
        )
        runCurrent()

        assertEquals(
            listOf("content://2", "content://1", "content://3"),
            viewModel.uiState.value.playlist?.items?.map(PlaylistItem::uriString),
        )

        viewModel.onAction(PlaylistDetailAction.PreviewMove(fromIndex = 1, toIndex = 2))
        viewModel.onAction(PlaylistDetailAction.StopMoveDrag)
        runCurrent()

        assertEquals(listOf(MoveCall(42, "content://1", 2)), repository.moveCalls)

        repository.moveGate?.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun rapidMoveRequestsMakeOneRepositoryCall() = runTest(dispatcher) {
        collectUiState()
        repository.playlist.value = playlist(
            items = listOf(item("content://1", 0), item("content://2", 1)),
        )
        repository.moveGate = CompletableDeferred()
        runCurrent()

        viewModel.onAction(PlaylistDetailAction.MoveItem("content://1", 1))
        viewModel.onAction(PlaylistDetailAction.MoveItem("content://2", 0))
        runCurrent()

        assertTrue(viewModel.uiState.value.isMoving)
        assertEquals(listOf(MoveCall(42, "content://1", 1)), repository.moveCalls)

        repository.moveGate?.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isMoving)
    }

    private fun TestScope.collectUiState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
    }
}

private data class MoveCall(val id: Long, val uri: String, val toIndex: Int)

private class FakeDetailPlaylistRepository : PlaylistRepository {
    val playlist = MutableStateFlow<Playlist?>(null)
    val refreshedIds = mutableListOf<Long>()
    val moveCalls = mutableListOf<MoveCall>()
    var refreshGate: CompletableDeferred<Unit>? = null
    var refreshFailure: Throwable? = null
    var refreshResult = PlaylistRefreshResult(42, 0, 0)
    var moveGate: CompletableDeferred<Unit>? = null
    var moveFailure: Throwable? = null

    override fun observePlaylists(): Flow<List<PlaylistSummary>> = MutableStateFlow(emptyList())

    override fun observePlaylist(id: Long): Flow<Playlist?> = playlist

    override suspend fun createEditable(name: String): Long = error("Not used")

    override suspend fun createLinked(
        name: String,
        type: PlaylistType,
        source: String,
    ): PlaylistRefreshResult = error("Not used")

    override suspend fun addItems(id: Long, items: List<PlaylistItemInput>): Int = error("Not used")

    override suspend fun moveItem(id: Long, uriString: String, toIndex: Int) {
        moveCalls += MoveCall(id, uriString, toIndex)
        moveGate?.await()
        moveFailure?.let { throw it }
    }

    override suspend fun refresh(id: Long): PlaylistRefreshResult {
        refreshedIds += id
        refreshGate?.await()
        refreshFailure?.let { throw it }
        return refreshResult
    }

    override suspend fun delete(id: Long) = error("Not used")
}

private fun playlist(
    type: PlaylistType = PlaylistType.EDITABLE,
    items: List<PlaylistItem> = emptyList(),
) = Playlist(
    id = 42,
    name = "Movies",
    type = type,
    source = if (type == PlaylistType.EDITABLE) null else "source",
    items = items,
    lastRefreshedAt = null,
)

private fun item(uri: String, position: Int) = PlaylistItem(
    uriString = uri,
    title = "Item ${position + 1}",
    position = position,
)
