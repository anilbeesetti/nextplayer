package dev.anilbeesetti.nextplayer.feature.playlist.screens.list

import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistNameConflictException
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistFileGrant
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistFileGrantRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRefreshResult
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistSourceException
import dev.anilbeesetti.nextplayer.core.model.Playlist
import dev.anilbeesetti.nextplayer.core.model.PlaylistItemInput
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
class PlaylistListViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakePlaylistRepository
    private lateinit var grantRepository: FakePlaylistFileGrantRepository
    private lateinit var viewModel: PlaylistListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakePlaylistRepository()
        grantRepository = FakePlaylistFileGrantRepository()
        viewModel = PlaylistListViewModel(repository, grantRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun firstRepositoryEmissionStopsLoadingAndPublishesPlaylists() = runTest(dispatcher) {
        collectUiState()
        val playlists = listOf(playlistSummary(id = 7, name = "Movies"))

        repository.playlists.emit(playlists)
        runCurrent()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(playlists, viewModel.uiState.value.playlists)
    }

    @Test
    fun editableCreationEmitsCreatedPlaylistId() = runTest(dispatcher) {
        collectUiState()
        repository.editableId = 42
        val event = async { viewModel.events.first() }

        viewModel.onAction(PlaylistListAction.CreateEditable("Movies"))
        advanceUntilIdle()

        assertEquals(PlaylistListEvent.Created(42), event.await())
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun nameConflictRemainsInFormState() = runTest(dispatcher) {
        collectUiState()
        repository.editableFailure = PlaylistNameConflictException()

        viewModel.onAction(PlaylistListAction.CreateEditable("Movies"))
        advanceUntilIdle()

        assertEquals("A playlist with this name already exists.", viewModel.uiState.value.formError)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun linkedCreationShowsProgressAndKeepsSourceErrorInForm() = runTest(dispatcher) {
        collectUiState()
        val gate = CompletableDeferred<Unit>()
        repository.createLinkedHandler = { _, _, _ ->
            gate.await()
            throw PlaylistSourceException(IOException("Source unavailable"))
        }

        viewModel.onAction(
            PlaylistListAction.CreateLinked("News", PlaylistType.M3U_URL, "https://example.test/list.m3u"),
        )
        runCurrent()

        assertTrue(viewModel.uiState.value.isSaving)

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals("Source unavailable", viewModel.uiState.value.formError)
    }

    @Test
    fun fileCreationPassesM3uFileTypeAndSource() = runTest(dispatcher) {
        collectUiState()
        repository.createLinkedHandler = { _, _, _ -> PlaylistRefreshResult(15, 2, 0) }
        val source = "content://documents/playlist.m3u8"

        val grant = PlaylistFileGrant(source, 1)
        viewModel.onAction(PlaylistListAction.CreateLinked("Playlist", PlaylistType.M3U_FILE, source, grant))
        advanceUntilIdle()

        assertEquals(LinkedCreateCall("Playlist", PlaylistType.M3U_FILE, source), repository.linkedCalls.single())
        assertEquals(listOf(grantRepository.reservations.single(), grant), grantRepository.retained)
        assertTrue(grantRepository.released.isEmpty())
    }

    @Test
    fun failedFileCreationReleasesPreparedGrant() = runTest(dispatcher) {
        collectUiState()
        val source = "content://documents/playlist.m3u8"
        val grant = PlaylistFileGrant(source, 2)
        repository.createLinkedHandler = { _, _, _ -> throw IOException("broken") }
        val event = async { viewModel.events.first() }

        viewModel.onAction(PlaylistListAction.CreateLinked("Playlist", PlaylistType.M3U_FILE, source, grant))
        advanceUntilIdle()

        assertEquals(listOf(grantRepository.reservations.single(), grant), grantRepository.released)
        assertTrue(grantRepository.retained.isEmpty())
        assertEquals(PlaylistListEvent.FileCreationFailed("broken"), event.await())
        assertEquals(null, viewModel.uiState.value.formError)
    }

    @Test
    fun cancelledFileCreationReleasesPreparedGrant() = runTest(dispatcher) {
        collectUiState()
        val source = "content://documents/playlist.m3u8"
        val grant = PlaylistFileGrant(source, 3)
        val started = CompletableDeferred<Unit>()
        repository.createLinkedHandler = { _, _, _ ->
            started.complete(Unit)
            CompletableDeferred<PlaylistRefreshResult>().await()
        }

        viewModel.onAction(PlaylistListAction.CreateLinked("Playlist", PlaylistType.M3U_FILE, source, grant))
        started.await()
        viewModel.cancelCreation()
        advanceUntilIdle()

        assertEquals(listOf(grantRepository.reservations.single(), grant), grantRepository.released)
    }

    @Test
    fun urlAndEditableCreationDoNotTouchFileGrants() = runTest(dispatcher) {
        collectUiState()

        viewModel.onAction(PlaylistListAction.CreateEditable("Movies"))
        advanceUntilIdle()
        viewModel.onAction(PlaylistListAction.CreateLinked("News", PlaylistType.M3U_URL, "https://example.test/list.m3u"))
        advanceUntilIdle()

        assertTrue(grantRepository.retained.isEmpty())
        assertTrue(grantRepository.released.isEmpty())
    }

    @Test
    fun deleteDelegatesPlaylistId() = runTest(dispatcher) {
        viewModel.onAction(PlaylistListAction.Delete(91))
        advanceUntilIdle()

        assertEquals(listOf(91L), repository.deletedIds)
    }

    @Test
    fun clearFormErrorRemovesExistingError() = runTest(dispatcher) {
        collectUiState()
        repository.editableFailure = PlaylistNameConflictException()
        viewModel.onAction(PlaylistListAction.CreateEditable("Movies"))
        advanceUntilIdle()

        viewModel.onAction(PlaylistListAction.ClearFormError)
        runCurrent()

        assertEquals(null, viewModel.uiState.value.formError)
    }

    private fun TestScope.collectUiState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
    }
}

private class FakePlaylistFileGrantRepository : PlaylistFileGrantRepository {
    val retained = mutableListOf<PlaylistFileGrant>()
    val released = mutableListOf<PlaylistFileGrant>()
    val reservations = mutableListOf<PlaylistFileGrant>()

    override suspend fun acquire(uri: String): PlaylistFileGrant? = PlaylistFileGrant(uri, 1)
    override suspend fun reserve(grant: PlaylistFileGrant): PlaylistFileGrant =
        grant.copy(token = grant.token + 10_000).also(reservations::add)
    override suspend fun retain(grant: PlaylistFileGrant) { retained += grant }
    override suspend fun release(grant: PlaylistFileGrant) { released += grant }
    override suspend fun releaseIfUnused(uri: String) = Unit
}

private data class LinkedCreateCall(val name: String, val type: PlaylistType, val source: String)

private class FakePlaylistRepository : PlaylistRepository {
    val playlists = MutableStateFlow<List<PlaylistSummary>>(emptyList())
    var editableId = 1L
    var editableFailure: Throwable? = null
    var createLinkedHandler: suspend (String, PlaylistType, String) -> PlaylistRefreshResult = { _, _, _ ->
        PlaylistRefreshResult(1, 0, 0)
    }
    val linkedCalls = mutableListOf<LinkedCreateCall>()
    val deletedIds = mutableListOf<Long>()

    override fun observePlaylists(): Flow<List<PlaylistSummary>> = playlists

    override fun observePlaylist(id: Long): Flow<Playlist?> = MutableSharedFlow()

    override suspend fun createEditable(name: String): Long {
        editableFailure?.let { throw it }
        return editableId
    }

    override suspend fun createLinked(
        name: String,
        type: PlaylistType,
        source: String,
    ): PlaylistRefreshResult {
        linkedCalls += LinkedCreateCall(name, type, source)
        return createLinkedHandler(name, type, source)
    }

    override suspend fun addItems(id: Long, items: List<PlaylistItemInput>): Int = error("Not used")

    override suspend fun moveItem(id: Long, uriString: String, toIndex: Int) = error("Not used")

    override suspend fun refresh(id: Long): PlaylistRefreshResult = error("Not used")

    override suspend fun delete(id: Long) {
        deletedIds += id
    }
}

private fun playlistSummary(id: Long, name: String) = PlaylistSummary(
    id = id,
    name = name,
    type = PlaylistType.EDITABLE,
    itemCount = 0,
    lastRefreshedAt = null,
)
