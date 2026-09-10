package dev.anilbeesetti.nextplayer.feature.more.screens.history

import android.os.Looper
import androidx.lifecycle.viewModelScope
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakeMediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakePreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class HistoryViewModelTest {
    private val history = MutableStateFlow(listOf(Video.sample))
    private val preferences = FakePreferencesRepository()
    private val repository = object : MediaRepository by FakeMediaRepository() {
        override fun observePlaybackHistory() = history
        override suspend fun clearPlaybackHistory() {
            history.value = emptyList()
        }
    }
    private val viewModel = HistoryViewModel(
        mediaRepository = repository,
        preferencesRepository = preferences,
        output = HistoryViewModel.Output(navigateUp = {}, playVideo = {}),
    )

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `history and preferences update without a state subscriber`() {
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(DataState.Success(listOf(Video.sample)), viewModel.state.value.history)

        val updatedVideo = Video.sample.copy(nameWithExtension = "Updated.mp4")
        history.value = listOf(updatedVideo)
        runBlocking { preferences.updateApplicationPreferences { it.copy(showExtensionField = true) } }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(DataState.Success(listOf(updatedVideo)), viewModel.state.value.history)
        assertEquals(preferences.applicationPreferences.value, viewModel.state.value.preferences)
    }

    @Test
    fun `clear history action updates state without resubscribing`() {
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.onAction(HistoryAction.ClearHistory)
        shadowOf(Looper.getMainLooper()).idle()
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
