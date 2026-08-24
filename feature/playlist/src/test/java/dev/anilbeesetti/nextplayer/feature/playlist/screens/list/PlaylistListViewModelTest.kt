package dev.anilbeesetti.nextplayer.feature.playlist.screens.list

import android.content.Context
import com.sun.net.httpserver.HttpServer
import dev.anilbeesetti.nextplayer.core.data.playlist.M3UDocumentPermissionManager
import dev.anilbeesetti.nextplayer.core.data.playlist.M3UParser
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakeMediaRepository
import dev.anilbeesetti.nextplayer.core.domain.SyncPlaylistsWithMediaUseCase
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import dev.anilbeesetti.nextplayer.core.ui.base.ActionState
import dev.anilbeesetti.nextplayer.feature.playlist.FakePlaylistRepository
import dev.anilbeesetti.nextplayer.feature.playlist.FakeSystemService
import java.net.InetSocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PlaylistListViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var repository: FakePlaylistRepository
    private lateinit var systemService: FakeSystemService

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = RuntimeEnvironment.getApplication()
        repository = FakePlaylistRepository()
        systemService = FakeSystemService()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun urlCreationParsesPersistsAndNavigatesWithoutEvents() = runTest(dispatcher) {
        val server = playlistServer()
        val openedIds = mutableListOf<Long>()
        val viewModel = viewModel(openedIds)
        val source = "http://127.0.0.1:${server.address.port}/News_List.m3u"
        try {
            viewModel.onAction(PlaylistUiAction.CreateM3UUrl(source))
            advanceUntilIdle()

            val call = repository.createM3UCalls.single()
            assertEquals(PlaylistType.M3U_URL, call.type)
            assertEquals(source, call.source)
            assertEquals("News List", call.playlist.playlistName)
            assertEquals(listOf("https://media.example/live"), call.playlist.items.map { it.uri })
            assertEquals(listOf(42L), openedIds)
            assertEquals(PlaylistCreationDialog.NONE, viewModel.state.value.creationDialog)
            assertTrue(viewModel.state.value.saveActionState is ActionState.Success)
            assertTrue(systemService.toasts.isEmpty())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun urlPersistenceFailureRemainsInlineAndDoesNotNavigate() = runTest(dispatcher) {
        val server = playlistServer()
        repository.createM3UFailure = IllegalStateException("Database unavailable")
        val openedIds = mutableListOf<Long>()
        val viewModel = viewModel(openedIds)
        try {
            viewModel.onAction(
                PlaylistUiAction.CreateM3UUrl(
                    "http://127.0.0.1:${server.address.port}/News_List.m3u",
                ),
            )
            advanceUntilIdle()

            assertTrue(viewModel.state.value.saveActionState is ActionState.Failed)
            assertEquals("Database unavailable", viewModel.state.value.saveActionState.errorMessage)
            assertTrue(openedIds.isEmpty())
            assertTrue(systemService.toasts.isEmpty())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun duplicateUrlCreationRequestsCreateOnlyOnePlaylist() = runTest(dispatcher) {
        val server = playlistServer()
        val viewModel = viewModel(mutableListOf())
        val source = "http://127.0.0.1:${server.address.port}/News_List.m3u"
        try {
            viewModel.onAction(PlaylistUiAction.CreateM3UUrl(source))
            viewModel.onAction(PlaylistUiAction.CreateM3UUrl(source))
            advanceUntilIdle()

            assertEquals(1, repository.createM3UCalls.size)
        } finally {
            server.stop(0)
        }
    }

    private fun viewModel(openedIds: MutableList<Long>) = PlaylistListViewModel(
        playlistRepository = repository,
        m3uParser = M3UParser(context, Dispatchers.Unconfined),
        documentPermissionManager = M3UDocumentPermissionManager(context),
        syncPlaylistsWithMedia = SyncPlaylistsWithMediaUseCase(
            mediaRepository = FakeMediaRepository(),
            playlistRepository = repository,
            context = context,
        ),
        systemService = systemService,
        output = PlaylistListViewModel.Output(
            openPlaylist = openedIds::add,
            openSettings = {},
        ),
    )

    private fun playlistServer(): HttpServer =
        HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/News_List.m3u") { exchange ->
                val body = "https://media.example/live".encodeToByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
}
