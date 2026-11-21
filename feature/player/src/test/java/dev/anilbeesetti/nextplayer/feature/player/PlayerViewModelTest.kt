package dev.anilbeesetti.nextplayer.feature.player

import androidx.lifecycle.viewModelScope
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakeMediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakePreferencesRepository
import dev.anilbeesetti.nextplayer.core.domain.GetSortedPlaylistUseCase
import dev.anilbeesetti.nextplayer.core.domain.GetSortedVideosUseCase
import dev.anilbeesetti.nextplayer.core.model.MediaInfo
import dev.anilbeesetti.nextplayer.core.model.SubtitleStreamInfo
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderMode
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderServiceState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    private val mediaInfoResult = CompletableDeferred<MediaInfo?>()
    private lateinit var viewModel: PlayerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher = dispatcher)
        val mediaRepository = object : MediaRepository by FakeMediaRepository() {
            override suspend fun getMediaInfo(uri: String): MediaInfo? =
                withContext(NonCancellable) { mediaInfoResult.await() }
        }
        viewModel = PlayerViewModel(
            mediaRepository = mediaRepository,
            preferencesRepository = preferences,
            getSortedPlaylistUseCase = GetSortedPlaylistUseCase(
                getSortedVideosUseCase = GetSortedVideosUseCase(
                    mediaRepository = mediaRepository,
                    preferencesRepository = preferences,
                    defaultDispatcher = dispatcher,
                ),
                preferencesRepository = preferences,
                context = RuntimeEnvironment.getApplication(),
                defaultDispatcher = dispatcher,
            ),
            output = PlayerViewModel.Output(
                navigateUp = {},
                selectSubtitle = {},
                selectAudio = {},
                playInBackground = {},
                setVideoDecoderMode = {},
                setAudioDecoderMode = {},
                tryDecoderFallback = {},
                requestRename = {},
            ),
        )
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `file details retain additional playback subtitles`() = runTest(context = dispatcher) {
        val embedded = SubtitleStreamInfo(
            index = 0,
            title = "Embedded",
            codecName = "SubRip",
            language = null,
            disposition = 0,
        )
        val external = embedded.copy(
            index = 1,
            title = "External",
        )
        val snapshot = MediaInfo(
            video = Video.sample.copy(uriString = "content://media/external/video/media/42"),
            subtitleStreams = listOf(
                embedded,
                external,
            ),
        )
        viewModel.onAction(PlayerAction.ShowMediaInfo(info = snapshot))
        mediaInfoResult.complete(
            snapshot.copy(
                video = snapshot.video.copy(format = "MP4"),
                subtitleStreams = listOf(embedded),
            ),
        )
        runCurrent()

        assertEquals(
            "MP4",
            viewModel.state.value.mediaInfo?.video?.format,
        )
        assertEquals(
            snapshot.subtitleStreams,
            viewModel.state.value.mediaInfo?.subtitleStreams,
        )
    }

    @Test
    fun `late details do not reopen a dismissed info dialog`() = runTest(context = dispatcher) {
        val snapshot = MediaInfo(
            video = Video.sample.copy(uriString = "content://media/external/video/media/42"),
        )
        viewModel.onAction(PlayerAction.ShowMediaInfo(info = snapshot))
        runCurrent()
        assertEquals(
            snapshot,
            viewModel.state.value.mediaInfo,
        )

        viewModel.onAction(PlayerAction.DismissMediaInfo)
        mediaInfoResult.complete(snapshot.copy(video = snapshot.video.copy(size = 10)))
        runCurrent()

        assertNull(viewModel.state.value.mediaInfo)
    }

    @Test
    fun `preference and decoder updates preserve paused playback without a state subscriber`() =
        runTest(context = dispatcher) {
            viewModel.onAction(PlayerAction.UpdatePlayWhenReady(playWhenReady = false))
            assertFalse(viewModel.state.value.playWhenReady)
            val decoderState = DecoderServiceState(videoMode = DecoderMode.HW_PLUS)
            viewModel.onAction(PlayerAction.UpdateDecoderServiceState(state = decoderState))

            preferences.updatePlayerPreferences { it.copy(playerBrightness = 0.4f) }
            runCurrent()

            assertFalse(viewModel.state.value.playWhenReady)
            assertEquals(
                0.4f,
                viewModel.state.value.playerPreferences.playerBrightness,
            )
            assertEquals(
                decoderState,
                viewModel.state.value.decoderServiceState,
            )

            viewModel.onAction(PlayerAction.UpdatePlayWhenReady(playWhenReady = true))
            assertTrue(viewModel.state.value.playWhenReady)
            assertEquals(
                0.4f,
                viewModel.state.value.playerPreferences.playerBrightness,
            )
        }

    @Test
    fun `subtitle offset update is saved to preferences`() = runTest(context = dispatcher) {
        viewModel.onAction(PlayerAction.UpdateSubtitleOffset(y = -0.4f))
        runCurrent()

        assertEquals(
            -0.4f,
            viewModel.state.value.playerPreferences.subtitleOffsetY,
        )
    }

    @Test
    fun `queued toggles use latest preferences and preserve playback state`() =
        runTest(context = dispatcher) {
            preferences.updatePlayerPreferences { it.copy(showRemainingTime = false) }
            viewModel.onAction(PlayerAction.UpdatePlayWhenReady(playWhenReady = false))
            viewModel.onAction(action = PlayerAction.ToggleTimeDisplay)
            viewModel.onAction(PlayerAction.UpdatePlayerBrightness(value = 0.7f))
            viewModel.onAction(action = PlayerAction.ToggleTimeDisplay)
            runCurrent()

            assertFalse(viewModel.state.value.playerPreferences.showRemainingTime)
            assertEquals(
                0.7f,
                viewModel.state.value.playerPreferences.playerBrightness,
            )
            assertFalse(viewModel.state.value.playWhenReady)

            viewModel.onAction(action = PlayerAction.ToggleTimeDisplay)
            runCurrent()
            assertTrue(viewModel.state.value.playerPreferences.showRemainingTime)
        }
}
