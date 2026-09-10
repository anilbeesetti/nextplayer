package dev.anilbeesetti.nextplayer.feature.player

import android.os.Looper
import androidx.lifecycle.viewModelScope
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakeMediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakePreferencesRepository
import dev.anilbeesetti.nextplayer.core.domain.GetSortedPlaylistUseCase
import dev.anilbeesetti.nextplayer.core.domain.GetSortedVideosUseCase
import dev.anilbeesetti.nextplayer.core.model.VideoContentScale
import dev.anilbeesetti.nextplayer.feature.player.state.VideoZoomEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PlayerViewModelTest {
    private val preferences = FakePreferencesRepository()
    private lateinit var viewModel: PlayerViewModel

    @Before
    fun setUp() {
        val mediaRepository = FakeMediaRepository()
        viewModel = PlayerViewModel(
            mediaRepository = mediaRepository,
            preferencesRepository = preferences,
            getSortedPlaylistUseCase = GetSortedPlaylistUseCase(
                getSortedVideosUseCase = GetSortedVideosUseCase(mediaRepository, preferences, Dispatchers.Default),
                preferencesRepository = preferences,
                context = RuntimeEnvironment.getApplication(),
                defaultDispatcher = Dispatchers.Default,
            ),
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `preference updates preserve paused playback state`() {
        assertTrue(viewModel.state.value.playWhenReady)
        viewModel.onAction(PlayerAction.UpdatePlayWhenReady(false))

        viewModel.onAction(PlayerAction.UpdateBrightness(0.6f))
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(viewModel.state.value.playWhenReady)
        assertEquals(0.6f, preferences.playerPreferences.value.playerBrightness)
        assertEquals(preferences.playerPreferences.value, viewModel.state.value.playerPreferences)

        viewModel.onAction(PlayerAction.UpdatePlayWhenReady(true))

        assertTrue(viewModel.state.value.playWhenReady)
        assertEquals(preferences.playerPreferences.value, viewModel.state.value.playerPreferences)
    }

    @Test
    fun `content scale action persists preferences and updates state`() {
        val contentScale = VideoContentScale.entries.first { it != preferences.playerPreferences.value.playerVideoZoom }

        viewModel.onAction(PlayerAction.UpdateVideoZoom(VideoZoomEvent.ContentScaleChanged(contentScale)))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(contentScale, preferences.playerPreferences.value.playerVideoZoom)
        assertEquals(contentScale, viewModel.state.value.playerPreferences?.playerVideoZoom)
    }
}
