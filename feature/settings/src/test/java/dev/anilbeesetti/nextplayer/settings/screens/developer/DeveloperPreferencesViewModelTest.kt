package dev.anilbeesetti.nextplayer.settings.screens.developer

import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakePreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.ArtworkStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeveloperPreferencesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakePreferencesRepository()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun stylesUpdateIndependentlyAndRestoreFromPreferences() = runTest(dispatcher) {
        fun viewModel() = DeveloperPreferencesViewModel(
            repository,
            DeveloperPreferencesViewModel.Output(navigateUp = {}),
        )
        val viewModel = viewModel()
        val original = repository.playerPreferences.value
        viewModel.onAction(DeveloperPreferencesEvent.ControlsArtwork(ArtworkStyle.AUDIO))
        advanceUntilIdle()
        assertEquals(
            original.copy(controlsArtworkStyle = ArtworkStyle.AUDIO),
            viewModel.state.value,
        )
        viewModel.onAction(DeveloperPreferencesEvent.QueueArtwork(ArtworkStyle.AUDIO))
        advanceUntilIdle()
        viewModel.onAction(DeveloperPreferencesEvent.ControlsArtwork(ArtworkStyle.VIDEO))
        advanceUntilIdle()
        assertEquals(
            original.copy(queueArtworkStyle = ArtworkStyle.AUDIO),
            viewModel().state.value,
        )
    }
}
