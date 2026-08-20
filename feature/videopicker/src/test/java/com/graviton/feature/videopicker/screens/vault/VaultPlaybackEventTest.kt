package com.graviton.feature.videopicker.screens.vault

import androidx.core.net.toUri
import com.graviton.core.data.repository.PreferencesRepository
import com.graviton.core.data.repository.UnhideResult
import com.graviton.core.data.repository.VaultPinRepository
import com.graviton.core.data.repository.VaultRepository
import com.graviton.core.domain.GetHiddenVideosUseCase
import com.graviton.core.model.ApplicationPreferences
import com.graviton.core.model.MediaInfo
import com.graviton.core.model.PlayerPreferences
import com.graviton.core.model.Video
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
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
class VaultPlaybackEventTest {

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
    fun `direct video action emits direct playback event`() = runTest(testDispatcher.scheduler) {
        val uri = "content://com.graviton.fileprovider/vault/1821".toUri()
        val viewModel = VaultViewModel(
            vaultRepository = FakeVaultRepository,
            vaultPinRepository = FakeVaultPinRepository,
            getHiddenVideosUseCase = GetHiddenVideosUseCase(FakeVaultRepository, testDispatcher),
            preferencesRepository = FakePreferencesRepository(),
        )
        val event = async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }

        viewModel.onAction(VaultAction.PlayVideo(Video.sample.copy(uriString = uri.toString())))
        advanceUntilIdle()

        assertEquals(VaultEvent.PlayVideo(uri), event.await())
    }

    private data object FakeVaultRepository : VaultRepository {
        override fun observeHiddenVideos(): Flow<List<Video>> = emptyFlow()
        override suspend fun hideVideos(videos: List<Video>) = Unit
        override suspend fun unhideVideos(videos: List<Video>) = UnhideResult()
        override suspend fun deleteHiddenVideos(videos: List<Video>) = Unit
        override suspend fun getHiddenVideoInfo(id: Long): MediaInfo? = null
    }

    private data object FakeVaultPinRepository : VaultPinRepository {
        override suspend fun hasPinSet(): Boolean = false
        override suspend fun setPin(pin: String) = Unit
        override suspend fun verifyPin(pin: String): Boolean = false
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
