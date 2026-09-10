package dev.anilbeesetti.nextplayer.feature.more.screens.history

import androidx.lifecycle.viewModelScope
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakeMediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakePreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
    private val history = MutableStateFlow(listOf(Video.sample))
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
        Dispatchers.setMain(dispatcher)
        viewModel = HistoryViewModel(
            mediaRepository = repository,
            preferencesRepository = preferences,
            output = HistoryViewModel.Output(navigateUp = {}, playVideo = {}),
        )
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `history and preferences update independently while subscribed`() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        val updatedVideo = Video.sample.copy(nameWithExtension = "Updated.mp4")
        history.value = listOf(updatedVideo)
        runCurrent()
        assertEquals(DataState.Success(listOf(updatedVideo)), viewModel.state.value.history)

        preferences.updateApplicationPreferences { it.copy(showExtensionField = true) }
        runCurrent()
        assertEquals(DataState.Success(listOf(updatedVideo)), viewModel.state.value.history)
        assertEquals(preferences.applicationPreferences.value, viewModel.state.value.preferences)
    }

    @Test
    fun `preferences update before history emits its first value`() = runTest(dispatcher) {
        viewModel.viewModelScope.cancel()
        val pendingHistory = MutableSharedFlow<List<Video>>()
        viewModel = HistoryViewModel(
            mediaRepository = object : MediaRepository by repository {
                override fun observePlaybackHistory() = pendingHistory
            },
            preferencesRepository = preferences,
            output = HistoryViewModel.Output(navigateUp = {}, playVideo = {}),
        )
        backgroundScope.launch { viewModel.state.collect {} }
        preferences.updateApplicationPreferences { it.copy(showExtensionField = true) }
        runCurrent()
        assertEquals(DataState.Loading, viewModel.state.value.history)
        assertTrue(viewModel.state.value.preferences.showExtensionField)
    }

    @Test
    fun `observers stop in background and refresh when the screen returns`() = runTest(dispatcher) {
        runCurrent()
        assertEquals(0, history.subscriptionCount.value)
        val subscription = backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        subscription.cancel()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0, history.subscriptionCount.value)

        history.value = emptyList()
        preferences.updateApplicationPreferences { it.copy(showExtensionField = true) }
        runCurrent()
        assertEquals(DataState.Success(listOf(Video.sample)), viewModel.state.value.history)
        assertFalse(viewModel.state.value.preferences.showExtensionField)

        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        assertEquals(DataState.Success(emptyList<Video>()), viewModel.state.value.history)
        assertTrue(viewModel.state.value.preferences.showExtensionField)
    }

    @Test
    fun `clear history action updates state without resubscribing`() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        viewModel.onAction(HistoryAction.ClearHistory)
        runCurrent()
        assertEquals(DataState.Success(emptyList<Video>()), viewModel.state.value.history)
    }

    @Test
    fun `actions use the current route output`() {
        var oldOutputCalled = false
        var navigatedUp = false
        val played = mutableListOf<String>()
        viewModel.output = HistoryViewModel.Output(navigateUp = { oldOutputCalled = true }, playVideo = { oldOutputCalled = true })
        viewModel.output = HistoryViewModel.Output(navigateUp = { navigatedUp = true }, playVideo = played::add)

        viewModel.onAction(HistoryAction.PlayVideo("content://video/1"))
        viewModel.onAction(HistoryAction.NavigateUp)

        assertEquals(listOf("content://video/1"), played)
        assertTrue(navigatedUp)
        assertFalse(oldOutputCalled)
    }
}
