package dev.anilbeesetti.nextplayer.feature.playlist.screens.detail

import android.content.Context
import android.net.Uri
import dev.anilbeesetti.nextplayer.core.data.playlist.M3UParser
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakeMediaRepository
import dev.anilbeesetti.nextplayer.core.domain.ObservePlaylistUseCase
import dev.anilbeesetti.nextplayer.core.model.PlaylistItemRecord
import dev.anilbeesetti.nextplayer.core.model.PlaylistRecord
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.feature.playlist.FakePlaylistRepository
import dev.anilbeesetti.nextplayer.feature.playlist.FakeSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PlaylistDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var repository: FakePlaylistRepository
    private lateinit var systemService: FakeSystemService
    private lateinit var mediaRepository: FakeMediaRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = RuntimeEnvironment.getApplication()
        repository = FakePlaylistRepository()
        systemService = FakeSystemService()
        mediaRepository = FakeMediaRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun fileRefreshReplacesItemsAndPreservesSavedName() = runTest(dispatcher) {
        val sourceFile = kotlin.io.path.createTempFile(suffix = ".m3u").toFile()
        sourceFile.writeText("#PLAYLIST:New source name\nhttps://media.example/new")
        repository.playlist.value = linkedRecord(Uri.fromFile(sourceFile).toString())
        val viewModel = viewModel()
        try {
            runCurrent()
            viewModel.onAction(PlaylistDetailUiAction.Refresh)
            advanceUntilIdle()

            assertEquals(
                listOf("https://media.example/new"),
                repository.replacementCalls.single().second.map { it.uri },
            )
            val playlist = (viewModel.state.value.playlistDataState as DataState.Success).value
            assertEquals("Saved name", playlist?.name)
            assertFalse(viewModel.state.value.isRefreshing)
            assertEquals(1, systemService.toasts.size)
        } finally {
            sourceFile.delete()
        }
    }

    @Test
    fun failedRefreshKeepsCachedItemsAndSkipsReplacement() = runTest(dispatcher) {
        repository.playlist.value = linkedRecord("file:///missing/list.m3u")
        val viewModel = viewModel()
        runCurrent()

        viewModel.onAction(PlaylistDetailUiAction.Refresh)
        advanceUntilIdle()

        val playlist = (viewModel.state.value.playlistDataState as DataState.Success).value
        assertEquals(listOf("https://media.example/cached"), playlist?.items?.map { it.uri })
        assertTrue(repository.replacementCalls.isEmpty())
        assertFalse(viewModel.state.value.isRefreshing)
        assertEquals(1, systemService.toasts.size)
    }

    @Test
    fun localRefreshIsIgnored() = runTest(dispatcher) {
        repository.playlist.value = linkedRecord(source = null).copy(type = PlaylistType.LOCAL)
        val viewModel = viewModel()
        runCurrent()

        viewModel.onAction(PlaylistDetailUiAction.Refresh)
        advanceUntilIdle()

        assertTrue(repository.replacementCalls.isEmpty())
        assertTrue(systemService.toasts.isEmpty())
    }

    private fun viewModel() = PlaylistDetailViewModel(
        observePlaylist = ObservePlaylistUseCase(repository, mediaRepository),
        playlistRepository = repository,
        m3uParser = M3UParser(context, Dispatchers.Unconfined),
        systemService = systemService,
        input = PlaylistDetailViewModel.Input(7),
        output = PlaylistDetailViewModel.Output(
            navigateUp = {},
            playPlaylist = { _, _ -> },
        ),
    )

    private fun linkedRecord(source: String?) = PlaylistRecord(
        id = 7,
        name = "Saved name",
        type = PlaylistType.M3U_FILE,
        source = source,
        items = listOf(
            PlaylistItemRecord(
                position = 0,
                uri = "https://media.example/cached",
                title = "Cached",
            ),
        ),
        lastRefreshedAt = 123,
    )
}
