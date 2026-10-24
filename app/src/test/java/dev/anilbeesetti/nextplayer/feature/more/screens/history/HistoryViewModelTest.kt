package dev.anilbeesetti.nextplayer.feature.more.screens.history

import androidx.lifecycle.viewModelScope
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakeMediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakePreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.settings.screens.medialibrary.MediaLibraryPreferencesUiEvent
import dev.anilbeesetti.nextplayer.settings.screens.medialibrary.MediaLibraryPreferencesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val history = MutableStateFlow(value = listOf(Video.sample))
    private val preferences = FakePreferencesRepository()
    private val repository = object : MediaRepository by FakeMediaRepository() {
        override fun observePlaybackHistory() = history
        override suspend fun clearPlaybackHistory() {
            history.value = emptyList()
        }
    }
    private lateinit var viewModel: HistoryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher = dispatcher)
        viewModel = HistoryViewModel(
            mediaRepository = repository,
            preferencesRepository = preferences,
            output = HistoryViewModel.Output(
                navigateUp = {},
                playVideo = {},
            ),
        )
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `history and preferences update independently without a state subscriber`() =
        runTest(context = dispatcher) {
            runCurrent()
            val updatedVideo = Video.sample.copy(nameWithExtension = "Updated.mp4")
            history.value = listOf(element = updatedVideo)
            runCurrent()
            assertEquals(
                DataState.Success(value = listOf(element = updatedVideo)),
                viewModel.state.value.history,
            )

            preferences.updateApplicationPreferences { it.copy(showExtensionField = true) }
            runCurrent()
            assertEquals(
                DataState.Success(value = listOf(element = updatedVideo)),
                viewModel.state.value.history,
            )
            assertEquals(
                preferences.applicationPreferences.value,
                viewModel.state.value.preferences,
            )
        }

    @Test
    fun `preferences update before history emits its first value`() =
        runTest(context = dispatcher) {
            viewModel.viewModelScope.cancel()
            val pendingHistory = MutableSharedFlow<List<Video>>()
            viewModel = HistoryViewModel(
                mediaRepository = object : MediaRepository by repository {
                    override fun observePlaybackHistory() = pendingHistory
                },
                preferencesRepository = preferences,
                output = HistoryViewModel.Output(
                    navigateUp = {},
                    playVideo = {},
                ),
            )
            preferences.updateApplicationPreferences { it.copy(showExtensionField = true) }
            runCurrent()
            assertEquals(
                DataState.Loading,
                viewModel.state.value.history,
            )
            assertTrue(viewModel.state.value.preferences.showExtensionField)
        }

    @Test
    fun `observers are cancelled with the view model`() = runTest(context = dispatcher) {
        runCurrent()
        assertEquals(
            1,
            history.subscriptionCount.value,
        )

        viewModel.viewModelScope.cancel()
        runCurrent()
        assertEquals(
            0,
            history.subscriptionCount.value,
        )
        history.value = emptyList()
        preferences.updateApplicationPreferences { it.copy(showExtensionField = true) }
        runCurrent()
        assertEquals(
            DataState.Success(value = listOf(Video.sample)),
            viewModel.state.value.history,
        )
        assertFalse(viewModel.state.value.preferences.showExtensionField)
    }

    @Test
    fun `clear history action updates state without a state subscriber`() =
        runTest(context = dispatcher) {
            runCurrent()
            viewModel.onAction(action = HistoryAction.ClearHistory)
            runCurrent()
            assertEquals(
                DataState.Success(value = emptyList<Video>()),
                viewModel.state.value.history,
            )
        }

    @Test
    fun `turning off history clears it and turning on preserves new entries`() =
        runTest(context = dispatcher) {
            runCurrent()
            viewModel.onAction(HistoryAction.SetHistoryEnabled(enabled = false))
            runCurrent()
            assertTrue(preferences.applicationPreferences.value.isHistoryPaused)
            assertTrue(viewModel.state.value.preferences.isHistoryPaused)
            assertEquals(
                DataState.Success(value = emptyList<Video>()),
                viewModel.state.value.history,
            )

            history.value = listOf(Video.sample)
            viewModel.onAction(HistoryAction.SetHistoryEnabled(enabled = true))
            runCurrent()
            assertFalse(preferences.applicationPreferences.value.isHistoryPaused)
            assertFalse(viewModel.state.value.preferences.isHistoryPaused)
            assertEquals(
                DataState.Success(value = listOf(Video.sample)),
                viewModel.state.value.history,
            )
        }

    @Test
    fun `media library settings also clear history only when turning it off`() =
        runTest(context = dispatcher) {
            val settingsViewModel = MediaLibraryPreferencesViewModel(
                preferencesRepository = preferences,
                mediaRepository = repository,
                output = MediaLibraryPreferencesViewModel.Output(
                    navigateUp = {},
                    openFolders = {},
                    openThumbnails = {},
                ),
            )
            try {
                runCurrent()
                settingsViewModel.onAction(
                    MediaLibraryPreferencesUiEvent.SetWatchHistoryEnabled(enabled = false),
                )
                runCurrent()
                assertTrue(preferences.applicationPreferences.value.isHistoryPaused)
                assertEquals(
                    emptyList<Video>(),
                    history.value,
                )

                history.value = listOf(Video.sample)
                settingsViewModel.onAction(
                    MediaLibraryPreferencesUiEvent.SetWatchHistoryEnabled(enabled = true),
                )
                runCurrent()
                assertFalse(preferences.applicationPreferences.value.isHistoryPaused)
                assertEquals(
                    listOf(Video.sample),
                    history.value,
                )
            } finally {
                settingsViewModel.viewModelScope.cancel()
            }
        }

    @Test
    fun `actions use the current route output`() {
        var oldOutputCalled = false
        var navigatedUp = false
        val played = mutableListOf<String>()
        viewModel.output = HistoryViewModel.Output(
            navigateUp = { oldOutputCalled = true },
            playVideo = {
                oldOutputCalled =
                    true
            },
        )
        viewModel.output = HistoryViewModel.Output(
            navigateUp = { navigatedUp = true },
            playVideo = played::add,
        )

        viewModel.onAction(HistoryAction.PlayVideo(uri = "content://video/1"))
        viewModel.onAction(action = HistoryAction.NavigateUp)

        assertEquals(
            listOf(element = "content://video/1"),
            played,
        )
        assertTrue(navigatedUp)
        assertFalse(oldOutputCalled)
    }
}
