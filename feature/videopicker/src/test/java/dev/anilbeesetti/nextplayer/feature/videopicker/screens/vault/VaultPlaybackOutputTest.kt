package dev.anilbeesetti.nextplayer.feature.videopicker.screens.vault

import android.net.Uri
import androidx.core.net.toUri
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.UnhideResult
import dev.anilbeesetti.nextplayer.core.data.repository.VaultPinRepository
import dev.anilbeesetti.nextplayer.core.data.repository.VaultRepository
import dev.anilbeesetti.nextplayer.core.domain.GetHiddenVideosUseCase
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.MediaInfo
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.feature.videopicker.state.SelectionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class VaultPlaybackOutputTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `direct video action invokes the current output without an event collector`() = runTest(testDispatcher.scheduler) {
        val uri = "content://dev.anilbeesetti.nextplayer.fileprovider/vault/1821".toUri()
        val viewModel = VaultViewModel(
            output = VaultViewModel.Output(navigateUp = {}, playVideo = {}, playVideos = {}),
            vaultRepository = FakeVaultRepository,
            vaultPinRepository = FakeVaultPinRepository,
            getHiddenVideosUseCase = GetHiddenVideosUseCase(FakeVaultRepository, testDispatcher),
            preferencesRepository = FakePreferencesRepository(),
        )
        val played = mutableListOf<Uri>()
        viewModel.output = VaultViewModel.Output(navigateUp = {}, playVideo = { error("Stale output") }, playVideos = {})
        viewModel.output = VaultViewModel.Output(navigateUp = {}, playVideo = played::add, playVideos = {})

        viewModel.onAction(VaultAction.PlayVideo(Video.sample.copy(uriString = uri.toString())))
        assertEquals(listOf(uri), played)
    }

    @Test
    fun `selected videos invoke output once and empty selection does not play`() = runTest(testDispatcher.scheduler) {
        val played = mutableListOf<List<Uri>>()
        val viewModel = VaultViewModel(
            output = VaultViewModel.Output(navigateUp = {}, playVideo = {}, playVideos = played::add),
            vaultRepository = FakeVaultRepository,
            vaultPinRepository = FakeVaultPinRepository,
            getHiddenVideosUseCase = GetHiddenVideosUseCase(FakeVaultRepository, testDispatcher),
            preferencesRepository = FakePreferencesRepository(),
        )
        advanceUntilIdle()
        viewModel.onAction(VaultAction.SubmitUnlockPin("1234"))
        advanceUntilIdle()

        viewModel.onAction(VaultAction.PlaySelected(emptySet()))
        viewModel.onAction(
            VaultAction.PlaySelected(
                setOf(
                    SelectionItem.Video(
                        name = Video.sample.nameWithExtension,
                        uriString = Video.sample.uriString,
                        path = Video.sample.path,
                    ),
                ),
            ),
        )

        assertEquals(listOf(listOf(Video.sample.uriString.toUri())), played)
    }

    private data object FakeVaultRepository : VaultRepository {
        override fun observeHiddenVideos(): Flow<List<Video>> = flowOf(listOf(Video.sample))
        override suspend fun hideVideos(videos: List<Video>) = Unit
        override suspend fun unhideVideos(videos: List<Video>) = UnhideResult()
        override suspend fun deleteHiddenVideos(videos: List<Video>) = Unit
        override suspend fun getHiddenVideoInfo(id: Long): MediaInfo? = null
    }

    private data object FakeVaultPinRepository : VaultPinRepository {
        override suspend fun hasPinSet(): Boolean = true
        override suspend fun setPin(pin: String) = Unit
        override suspend fun isBiometricEnabled(): Boolean = false
        override suspend fun setBiometricEnabled(enabled: Boolean) = Unit
        override suspend fun verifyPin(pin: String): Boolean = pin == "1234"
        override suspend fun hasShownHideConfirmation(): Boolean = false
        override suspend fun setHideConfirmationShown() = Unit
    }

    private class FakePreferencesRepository : PreferencesRepository {
        override val applicationPreferences: StateFlow<ApplicationPreferences> = MutableStateFlow(ApplicationPreferences())
        override val playerPreferences: StateFlow<PlayerPreferences> = MutableStateFlow(PlayerPreferences())

        override suspend fun updateApplicationPreferences(
            transform: suspend (ApplicationPreferences) -> ApplicationPreferences,
        ) = Unit

        override suspend fun updatePlayerPreferences(
            transform: suspend (PlayerPreferences) -> PlayerPreferences,
        ) = Unit

        override suspend fun resetPreferences() = Unit
    }
}
