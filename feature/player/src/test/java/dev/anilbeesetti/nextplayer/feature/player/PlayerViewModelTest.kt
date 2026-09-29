package dev.anilbeesetti.nextplayer.feature.player

import androidx.lifecycle.viewModelScope
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakeMediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakePreferencesRepository
import dev.anilbeesetti.nextplayer.core.domain.GetSortedPlaylistUseCase
import dev.anilbeesetti.nextplayer.core.domain.GetSortedVideosUseCase
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderServiceState
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
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
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlayerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val preferences = FakePreferencesRepository()
    private lateinit var viewModel: PlayerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val mediaRepository = FakeMediaRepository()
        viewModel = PlayerViewModel(
            mediaRepository = mediaRepository,
            preferencesRepository = preferences,
            getSortedPlaylistUseCase = GetSortedPlaylistUseCase(
                getSortedVideosUseCase = GetSortedVideosUseCase(mediaRepository, preferences, dispatcher),
                preferencesRepository = preferences,
                context = RuntimeEnvironment.getApplication(),
                defaultDispatcher = dispatcher,
            ),
            output = PlayerViewModel.Output({}, {}, {}, {}, {}, {}, {}),
        )
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `preference and decoder updates preserve paused playback without a state subscriber`() = runTest(dispatcher) {
        viewModel.onAction(PlayerAction.UpdatePlayWhenReady(false))
        assertFalse(viewModel.state.value.playWhenReady)
        val decoderState = DecoderServiceState(videoMode = DecoderMode.SOFTWARE)
        viewModel.onAction(PlayerAction.UpdateDecoderServiceState(decoderState))

        preferences.updatePlayerPreferences { it.copy(playerBrightness = 0.4f) }
        runCurrent()

        assertFalse(viewModel.state.value.playWhenReady)
        assertEquals(0.4f, viewModel.state.value.playerPreferences.playerBrightness)
        assertEquals(decoderState, viewModel.state.value.decoderServiceState)

        viewModel.onAction(PlayerAction.UpdatePlayWhenReady(true))
        assertTrue(viewModel.state.value.playWhenReady)
        assertEquals(0.4f, viewModel.state.value.playerPreferences.playerBrightness)
    }

    @Test
    fun `queued toggles use latest preferences and preserve playback state`() = runTest(dispatcher) {
        preferences.updatePlayerPreferences { it.copy(showRemainingTime = false) }
        viewModel.onAction(PlayerAction.UpdatePlayWhenReady(false))
        viewModel.onAction(PlayerAction.ToggleTimeDisplay)
        viewModel.onAction(PlayerAction.UpdatePlayerBrightness(0.7f))
        viewModel.onAction(PlayerAction.ToggleTimeDisplay)
        runCurrent()

        assertFalse(viewModel.state.value.playerPreferences.showRemainingTime)
        assertEquals(0.7f, viewModel.state.value.playerPreferences.playerBrightness)
        assertFalse(viewModel.state.value.playWhenReady)

        viewModel.onAction(PlayerAction.ToggleTimeDisplay)
        runCurrent()
        assertTrue(viewModel.state.value.playerPreferences.showRemainingTime)
    }
}
