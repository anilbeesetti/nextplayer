package dev.anilbeesetti.nextplayer.feature.videopicker.screens.search

import androidx.lifecycle.viewModelScope
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.SearchHistoryRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakeMediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakePreferencesRepository
import dev.anilbeesetti.nextplayer.core.domain.GetPopularFoldersUseCase
import dev.anilbeesetti.nextplayer.core.domain.GetSortedFoldersUseCase
import dev.anilbeesetti.nextplayer.core.domain.GetSortedVideosUseCase
import dev.anilbeesetti.nextplayer.core.domain.SearchMediaUseCase
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
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
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val video = Video.sample.copy(nameWithExtension = "Alpha.mp4")
    private val videos = MutableStateFlow(listOf(video))
    private val folders = MutableStateFlow(emptyList<Folder>())
    private val history = MutableStateFlow(emptyList<String>())
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val preferences = FakePreferencesRepository()
        val repository = object : MediaRepository by FakeMediaRepository() {
            override fun observeVideos(folderPath: String?) = videos
            override fun observeFolders(folderPath: String?) = folders
        }
        val sortedVideos = GetSortedVideosUseCase(repository, preferences, dispatcher)
        val sortedFolders = GetSortedFoldersUseCase(repository, preferences, dispatcher)
        viewModel = SearchViewModel(
            searchMediaUseCase = SearchMediaUseCase(sortedVideos, sortedFolders, dispatcher),
            getPopularFoldersUseCase = GetPopularFoldersUseCase(sortedFolders, sortedVideos, dispatcher),
            searchHistoryRepository = object : SearchHistoryRepository {
                override val searchHistory = history
                override suspend fun addSearchQuery(query: String) { history.value += query }
                override suspend fun removeSearchQuery(query: String) { history.value -= query }
                override suspend fun clearHistory() { history.value = emptyList() }
            },
            preferencesRepository = preferences,
            output = SearchViewModel.Output(navigateUp = {}, playVideo = {}, openFolder = {}),
        )
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `search debounces queries without letting old results clear the new loading state`() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()
        viewModel.onAction(SearchUiEvent.OnQueryChange("Alpha"))
        runCurrent()
        assertEquals("Alpha", viewModel.state.value.query)
        assertTrue(viewModel.state.value.isSearching)
        advanceTimeBy(300)
        runCurrent()
        assertEquals(listOf(video), viewModel.state.value.searchResults.videos)
        assertFalse(viewModel.state.value.isSearching)

        viewModel.onAction(SearchUiEvent.OnQueryChange("Beta"))
        videos.value = listOf(video.copy(size = video.size + 1))
        runCurrent()
        assertTrue(viewModel.state.value.isSearching)
        advanceTimeBy(299)
        runCurrent()
        assertTrue(viewModel.state.value.isSearching)
        advanceTimeBy(1)
        runCurrent()
        assertFalse(viewModel.state.value.isSearching)
        assertTrue(viewModel.state.value.searchResults.isEmpty)

        viewModel.onAction(SearchUiEvent.OnHistoryItemClick("Beta"))
        runCurrent()
        assertFalse(viewModel.state.value.isSearching)
        assertEquals(listOf("Beta"), viewModel.state.value.searchHistory)
    }

    @Test
    fun `all search sources stop without subscribers and resume the current query`() = runTest(dispatcher) {
        runCurrent()
        assertEquals(0, videos.subscriptionCount.value)
        assertEquals(0, folders.subscriptionCount.value)
        assertEquals(0, history.subscriptionCount.value)
        val subscriber = backgroundScope.launch { viewModel.state.collect() }
        viewModel.onAction(SearchUiEvent.OnQueryChange("Alpha"))
        runCurrent()
        advanceTimeBy(300)
        runCurrent()
        assertEquals(listOf(video), viewModel.state.value.searchResults.videos)

        subscriber.cancel()
        runCurrent()
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(0, videos.subscriptionCount.value)
        assertEquals(0, folders.subscriptionCount.value)
        assertEquals(0, history.subscriptionCount.value)
        videos.value = emptyList()

        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()
        advanceTimeBy(300)
        runCurrent()
        assertEquals("Alpha", viewModel.state.value.query)
        assertTrue(viewModel.state.value.searchResults.isEmpty)
        assertFalse(viewModel.state.value.isSearching)
    }
}
