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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    fun playAllEmitsFullOrderedListAndFirstStart() = runTest(dispatcher) {
        collectUiState()
        repository.playlist.value = playlist(
            items = listOf(item("content://1", 0), item("content://2", 1)),
        )
        runCurrent()
        val event = async { viewModel.events.first() }

        viewModel.onAction(PlaylistDetailAction.PlayAll)

        assertEquals(
            PlaylistDetailEvent.Play(
                uris = listOf("content://1", "content://2"),
                startUri = "content://1",
            ),
            event.await(),
        )
    }

    @Test
    fun playItemEmitsFullListAndSelectedStart() = runTest(dispatcher) {
        collectUiState()
        repository.playlist.value = playlist(
            items = listOf(item("content://1", 0), item("content://2", 1)),
        )
        runCurrent()
        val event = async { viewModel.events.first() }

        viewModel.onAction(PlaylistDetailAction.PlayItem("content://2"))

        assertEquals(
            PlaylistDetailEvent.Play(listOf("content://1", "content://2"), "content://2"),
            event.await(),
        )
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
        repository.playlist.value = playlist(type = PlaylistType.EDITABLE)
        runCurrent()

        viewModel.onAction(PlaylistDetailAction.MoveItem("content://2", 0))
        advanceUntilIdle()

        assertEquals(listOf(MoveCall(42, "content://2", 0)), repository.moveCalls)
    }

    @Test
    fun deleteDelegatesIdAndEmitsDeleted() = runTest(dispatcher) {
        val event = async { viewModel.events.first() }

        viewModel.onAction(PlaylistDetailAction.Delete)
        advanceUntilIdle()

        assertEquals(listOf(42L), repository.deletedIds)
        assertEquals(PlaylistDetailEvent.Deleted, event.await())
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
    val deletedIds = mutableListOf<Long>()
    var refreshGate: CompletableDeferred<Unit>? = null
    var refreshFailure: Throwable? = null
    var refreshResult = PlaylistRefreshResult(42, 0, 0)

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
    }

    override suspend fun refresh(id: Long): PlaylistRefreshResult {
        refreshedIds += id
        refreshGate?.await()
        refreshFailure?.let { throw it }
        return refreshResult
    }

    override suspend fun delete(id: Long) {
        deletedIds += id
    }
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
