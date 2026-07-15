package dev.anilbeesetti.nextplayer.feature.videopicker.screens.mediapicker

import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRefreshResult
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import dev.anilbeesetti.nextplayer.core.model.MediaViewMode
import dev.anilbeesetti.nextplayer.core.model.Playlist
import dev.anilbeesetti.nextplayer.core.model.PlaylistItemInput
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.feature.videopicker.state.SelectionItem
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistSelectionMapperTest {

    @Test
    fun folderSelectionUsesDirectChildrenInFolderMode() = runTest {
        val resolver = resolver(
            folderVideos = listOf(
                video("content://direct", "/Movies"),
                video("content://nested", "/Movies/Nested"),
            ),
        )

        val result = resolver.resolve(
            linkedSetOf(SelectionItem.Folder(name = "Movies", path = "/Movies")),
            MediaViewMode.FOLDERS,
        )

        assertEquals(listOf("content://direct"), result.map { it.uriString })
    }

    @Test
    fun folderSelectionUsesRecursiveResultsOutsideFolderMode() = runTest {
        val resolver = resolver(
            folderVideos = listOf(
                video("content://direct", "/Movies"),
                video("content://nested", "/Movies/Nested"),
            ),
        )

        val result = resolver.resolve(
            linkedSetOf(SelectionItem.Folder(name = "Movies", path = "/Movies")),
            MediaViewMode.VIDEOS,
        )

        assertEquals(listOf("content://direct", "content://nested"), result.map { it.uriString })
    }

    @Test
    fun overlappingFolderAndVideoSelectionDeduplicatesFirstSeenOrder() = runTest {
        val first = video("content://1", "/Movies")
        val second = video("content://2", "/Movies")
        val resolver = PlaylistSelectionMapper(
            findVideoByUri = { uri -> listOf(first, second).firstOrNull { it.uriString == uri } },
            videosInFolder = { listOf(first, second) },
        )
        val selection = linkedSetOf<SelectionItem>(
            SelectionItem.Video(name = "First", uriString = first.uriString, path = first.path),
            SelectionItem.Folder(name = "Movies", path = "/Movies"),
        )

        val result = resolver.resolve(selection, MediaViewMode.VIDEOS)

        assertEquals(listOf("content://1", "content://2"), result.map { it.uriString })
    }

    @Test
    fun onlyEditablePlaylistsArePublishedAsTargets() = runTest {
        val repository = FakePlaylistRepository()
        val controller = controller(repository)
        repository.playlists.value = listOf(
            summary(1, "Editable", PlaylistType.EDITABLE),
            summary(2, "URL", PlaylistType.M3U_URL),
            summary(3, "File", PlaylistType.M3U_FILE),
        )

        advanceUntilIdle()

        assertEquals(listOf(1L), controller.editablePlaylists.value.map { it.id })
    }

    @Test
    fun successfulAddEmitsCountAndCompletesDialog() = runTest {
        val repository = FakePlaylistRepository().apply {
            addedCount = 2
            playlists.value = listOf(summary(7, "Editable", PlaylistType.EDITABLE))
        }
        val controller = controller(repository)
        val event = async { controller.events.first() }
        controller.showAddToPlaylist(selection())
        advanceUntilIdle()
        val beforeToken = controller.state.value.completionToken

        controller.addSelectionToPlaylist(7)
        advanceUntilIdle()

        assertEquals(MediaPickerEvent.PlaylistItemsAdded(2), event.await())
        assertEquals(7L, repository.addCalls.single().playlistId)
        assertFalse(controller.state.value.isVisible)
        assertEquals(beforeToken + 1, controller.state.value.completionToken)
    }

    @Test
    fun repositoryFailureKeepsDialogAndPendingSelectionForRetry() = runTest {
        val repository = FakePlaylistRepository().apply {
            addFailure = IOException("disk full")
            playlists.value = listOf(summary(7, "Editable", PlaylistType.EDITABLE))
        }
        val controller = controller(repository)
        controller.showAddToPlaylist(selection())
        advanceUntilIdle()

        controller.addSelectionToPlaylist(7)
        advanceUntilIdle()

        assertTrue(controller.state.value.isVisible)
        assertFalse(controller.state.value.isSaving)
        assertTrue(controller.state.value.error?.isNotBlank() == true)

        repository.addFailure = null
        controller.addSelectionToPlaylist(7)
        advanceUntilIdle()
        assertEquals(2, repository.addCalls.size)
        assertFalse(controller.state.value.isVisible)
    }

    @Test
    fun createAndAddCreatesEditablePlaylistBeforeAddingPendingItems() = runTest {
        val repository = FakePlaylistRepository().apply { editableId = 42 }
        val controller = controller(repository)
        controller.showAddToPlaylist(selection())
        advanceUntilIdle()

        controller.createPlaylistAndAddSelection("  Road Trip  ")
        advanceUntilIdle()

        assertEquals(listOf("  Road Trip  "), repository.createdNames)
        assertEquals(42L, repository.addCalls.single().playlistId)
        assertEquals(listOf("content://1"), repository.addCalls.single().items.map { it.uriString })
    }

    @Test
    fun createAndAddRetryReusesCreatedPlaylistAfterAddFailure() = runTest {
        val repository = FakePlaylistRepository().apply {
            editableId = 42
            addFailure = IOException("disk full")
        }
        val controller = controller(repository)
        controller.showAddToPlaylist(selection())
        advanceUntilIdle()

        controller.createPlaylistAndAddSelection(" Road Trip ")
        advanceUntilIdle()
        repository.addFailure = null
        controller.createPlaylistAndAddSelection("road trip")
        advanceUntilIdle()

        assertEquals(listOf(" Road Trip "), repository.createdNames)
        assertEquals(listOf(42L, 42L), repository.addCalls.map { it.playlistId })
        assertFalse(controller.state.value.isVisible)
    }

    @Test
    fun changingNameAfterPartialCreateRequiresRetryingCreatedPlaylist() = runTest {
        val repository = FakePlaylistRepository().apply {
            editableId = 42
            addFailure = IOException("disk full")
        }
        val controller = controller(repository)
        controller.showAddToPlaylist(selection())
        advanceUntilIdle()
        controller.createPlaylistAndAddSelection("Road Trip")
        advanceUntilIdle()

        repository.addFailure = null
        controller.createPlaylistAndAddSelection("Favorites")
        advanceUntilIdle()

        assertEquals(listOf("Road Trip"), repository.createdNames)
        assertEquals(1, repository.addCalls.size)
        assertTrue(controller.state.value.error?.contains("Road Trip") == true)
    }

    @Test
    fun emptyResolvedSelectionShowsError() = runTest {
        val controller = controller(
            repository = FakePlaylistRepository(),
            resolver = resolver(folderVideos = emptyList()),
        )

        controller.showAddToPlaylist(selection())
        advanceUntilIdle()

        assertTrue(controller.state.value.isVisible)
        assertFalse(controller.state.value.isSaving)
        assertTrue(controller.state.value.error?.isNotBlank() == true)
    }

    @Test
    fun resolutionCancellationIsNotRenderedAsAnError() = runTest {
        var resolutionAttempts = 0
        val resolvedVideo = video("content://1", "/Movies")
        val controller = controller(
            repository = FakePlaylistRepository(),
            resolver = PlaylistSelectionMapper(
                findVideoByUri = {
                    resolutionAttempts++
                    if (resolutionAttempts == 1) throw CancellationException("cancelled")
                    resolvedVideo
                },
                videosInFolder = { emptyList() },
            ),
        )

        controller.showAddToPlaylist(selection())
        advanceUntilIdle()

        assertNull(controller.state.value.error)
        assertFalse(controller.state.value.isSaving)
        assertTrue(controller.state.value.isVisible)

        controller.showAddToPlaylist(selection())
        advanceUntilIdle()

        assertEquals(2, resolutionAttempts)
        assertFalse(controller.state.value.isSaving)
        assertNull(controller.state.value.error)
    }

    @Test
    fun createAndAddCancellationRetainsCreatedPlaylistForRetry() = runTest {
        val repository = FakePlaylistRepository().apply {
            editableId = 42
            addFailure = CancellationException("cancelled")
        }
        val controller = controller(repository)
        controller.showAddToPlaylist(selection())
        advanceUntilIdle()

        controller.createPlaylistAndAddSelection(" Road Trip ")
        advanceUntilIdle()

        assertNull(controller.state.value.error)
        assertFalse(controller.state.value.isSaving)
        assertTrue(controller.state.value.isVisible)

        repository.addFailure = null
        controller.createPlaylistAndAddSelection("road trip")
        advanceUntilIdle()

        assertEquals(listOf(" Road Trip "), repository.createdNames)
        assertEquals(listOf(42L, 42L), repository.addCalls.map { it.playlistId })
        assertFalse(controller.state.value.isVisible)
    }

    @Test
    fun dismissAndNewSelectionClearPartialCreateRetryIdentity() = runTest {
        val repository = FakePlaylistRepository().apply {
            editableId = 42
            addFailure = IOException("disk full")
        }
        val controller = controller(repository)
        controller.showAddToPlaylist(selection())
        advanceUntilIdle()
        controller.createPlaylistAndAddSelection("Road Trip")
        advanceUntilIdle()

        controller.dismiss()
        controller.showAddToPlaylist(selection())
        advanceUntilIdle()
        repository.editableId = 43
        repository.addFailure = null
        controller.createPlaylistAndAddSelection("Favorites")
        advanceUntilIdle()

        assertEquals(listOf("Road Trip", "Favorites"), repository.createdNames)
        assertEquals(listOf(42L, 43L), repository.addCalls.map { it.playlistId })
    }

    private fun kotlinx.coroutines.test.TestScope.controller(
        repository: FakePlaylistRepository,
        resolver: PlaylistSelectionMapper = resolver(folderVideos = listOf(video("content://1", "/Movies"))),
    ): PlaylistSelectionController = PlaylistSelectionController(
        repository = repository,
        resolver = resolver,
        mediaViewMode = { MediaViewMode.VIDEOS },
        scope = backgroundScope,
        dispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    private fun resolver(folderVideos: List<Video>) = PlaylistSelectionMapper(
        findVideoByUri = { uri -> folderVideos.firstOrNull { it.uriString == uri } },
        videosInFolder = { folderVideos },
    )

    private fun selection(): Set<SelectionItem> = linkedSetOf(
        SelectionItem.Video(name = "One", uriString = "content://1", path = "/Movies/one.mp4"),
    )
}

private data class AddCall(val playlistId: Long, val items: List<PlaylistItemInput>)

private class FakePlaylistRepository : PlaylistRepository {
    val playlists = MutableStateFlow<List<PlaylistSummary>>(emptyList())
    val addCalls = mutableListOf<AddCall>()
    val createdNames = mutableListOf<String>()
    var editableId = 1L
    var addedCount = 1
    var addFailure: Throwable? = null

    override fun observePlaylists(): Flow<List<PlaylistSummary>> = playlists
    override fun observePlaylist(id: Long): Flow<Playlist?> = emptyFlow()

    override suspend fun createEditable(name: String): Long {
        createdNames += name
        return editableId
    }

    override suspend fun addItems(id: Long, items: List<PlaylistItemInput>): Int {
        addCalls += AddCall(id, items)
        addFailure?.let { throw it }
        return addedCount
    }

    override suspend fun createLinked(name: String, type: PlaylistType, source: String): PlaylistRefreshResult =
        error("Not used")

    override suspend fun moveItem(id: Long, uriString: String, toIndex: Int) = error("Not used")
    override suspend fun refresh(id: Long): PlaylistRefreshResult = error("Not used")
    override suspend fun delete(id: Long) = error("Not used")
}

private fun summary(id: Long, name: String, type: PlaylistType) = PlaylistSummary(
    id = id,
    name = name,
    type = type,
    itemCount = 0,
    lastRefreshedAt = null,
)

private fun video(uri: String, parentPath: String) = Video(
    id = uri.hashCode().toLong(),
    path = "$parentPath/video.mp4",
    parentPath = parentPath,
    duration = 1,
    uriString = uri,
    nameWithExtension = "video.mp4",
    width = 1,
    height = 1,
    size = 1,
)
