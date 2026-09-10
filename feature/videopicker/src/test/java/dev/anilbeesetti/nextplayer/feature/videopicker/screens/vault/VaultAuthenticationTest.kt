package dev.anilbeesetti.nextplayer.feature.videopicker.screens.vault

import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.UnhideResult
import dev.anilbeesetti.nextplayer.core.data.repository.VaultPinRepository
import dev.anilbeesetti.nextplayer.core.data.repository.VaultRepository
import dev.anilbeesetti.nextplayer.core.domain.GetHiddenVideosUseCase
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.MediaInfo
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.Video
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class VaultAuthenticationTest {
    private val dispatcher = StandardTestDispatcher()
    private val vaultRepository = FakeVaultRepository()
    private var biometricEnabled = false

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `biometrics cannot unlock before PIN configuration is loaded`() = runTest(dispatcher.scheduler) {
        val hasPin = CompletableDeferred<Boolean>()
        val viewModel = createViewModel(hasPin)
        advanceUntilIdle()

        viewModel.onAction(VaultAction.BiometricAuthenticated)

        assertEquals(VaultStage.LOADING, viewModel.state.value.stage)
        assertEquals(0, vaultRepository.observations)
        hasPin.complete(true)
        advanceUntilIdle()
        assertEquals(VaultStage.LOCKED, viewModel.state.value.stage)
    }

    @Test
    fun `biometrics cannot bypass PIN setup or confirmation`() = runTest(dispatcher.scheduler) {
        val viewModel = createViewModel(CompletableDeferred(false))
        advanceUntilIdle()

        viewModel.onAction(VaultAction.BiometricAuthenticated)
        assertEquals(VaultStage.SET_PIN, viewModel.state.value.stage)
        viewModel.onAction(VaultAction.SubmitNewPin("1234"))
        viewModel.onAction(VaultAction.BiometricAuthenticated)
        assertEquals(VaultStage.CONFIRM_PIN, viewModel.state.value.stage)
        assertEquals(0, vaultRepository.observations)
    }

    @Test
    fun `successful biometrics unlock once and clear earlier PIN errors`() = runTest(dispatcher.scheduler) {
        biometricEnabled = true
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAction(VaultAction.SubmitUnlockPin("0000"))
        advanceUntilIdle()
        assertEquals(VaultStage.LOCKED, viewModel.state.value.stage)
        assertEquals(1, viewModel.state.value.pinErrorCount)

        viewModel.onAction(VaultAction.BiometricAuthenticated)
        advanceUntilIdle()
        viewModel.onAction(VaultAction.BiometricAuthenticated)
        advanceUntilIdle()

        assertEquals(VaultStage.UNLOCKED, viewModel.state.value.stage)
        assertEquals(0, viewModel.state.value.pinErrorCount)
        assertEquals(listOf(Video.sample), viewModel.state.value.hiddenVideos)
        assertEquals(1, vaultRepository.observations)
    }

    @Test
    fun `PIN fallback still unlocks the vault`() = runTest(dispatcher.scheduler) {
        biometricEnabled = true
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAction(VaultAction.SubmitUnlockPin("1234"))
        advanceUntilIdle()

        assertEquals(VaultStage.UNLOCKED, viewModel.state.value.stage)
        assertEquals(listOf(Video.sample), viewModel.state.value.hiddenVideos)
    }

    @Test
    fun `biometrics cannot unlock without opting in`() = runTest(dispatcher.scheduler) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAction(VaultAction.BiometricAuthenticated)
        advanceUntilIdle()

        assertEquals(VaultStage.LOCKED, viewModel.state.value.stage)
        assertEquals(0, vaultRepository.observations)
    }

    @Test
    fun `confirming a new PIN offers biometrics without enabling it`() = runTest(dispatcher.scheduler) {
        val viewModel = createViewModel(CompletableDeferred(false))
        advanceUntilIdle()
        viewModel.onAction(VaultAction.SubmitNewPin("1234"))
        viewModel.onAction(VaultAction.SubmitPinConfirmation("1234"))
        advanceUntilIdle()

        assertEquals(VaultStage.BIOMETRIC_SETUP, viewModel.state.value.stage)
        assertFalse(biometricEnabled)
        viewModel.onAction(VaultAction.BiometricAuthenticated)
        assertEquals(VaultStage.BIOMETRIC_SETUP, viewModel.state.value.stage)
        assertEquals(0, vaultRepository.observations)

        viewModel.onAction(VaultAction.CompleteBiometricSetup(true))
        advanceUntilIdle()

        assertTrue(biometricEnabled)
        assertTrue(viewModel.state.value.biometricEnabled)
        assertEquals(VaultStage.HOW_TO_FIND_INFO, viewModel.state.value.stage)
        val reopened = createViewModel()
        advanceUntilIdle()
        assertTrue(reopened.state.value.biometricEnabled)
    }

    @Test
    fun `skipping biometric setup keeps the next unlock PIN only`() = runTest(dispatcher.scheduler) {
        val viewModel = createViewModel(CompletableDeferred(false))
        advanceUntilIdle()
        viewModel.onAction(VaultAction.SubmitNewPin("1234"))
        viewModel.onAction(VaultAction.SubmitPinConfirmation("1234"))
        advanceUntilIdle()
        viewModel.onAction(VaultAction.CompleteBiometricSetup(false))
        advanceUntilIdle()

        assertFalse(biometricEnabled)
        assertEquals(VaultStage.HOW_TO_FIND_INFO, viewModel.state.value.stage)
        val reopened = createViewModel()
        advanceUntilIdle()
        reopened.onAction(VaultAction.BiometricAuthenticated)
        assertEquals(VaultStage.LOCKED, reopened.state.value.stage)
    }

    @Test
    fun `biometric settings cannot be changed from the locked screen`() = runTest(dispatcher.scheduler) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAction(VaultAction.SetBiometricEnabled(true))
        viewModel.onAction(VaultAction.CompleteBiometricSetup(true))
        advanceUntilIdle()

        assertFalse(biometricEnabled)
        assertEquals(VaultStage.LOCKED, viewModel.state.value.stage)
    }

    @Test
    fun `biometrics can be enabled in the unlocked vault and disabled for later visits`() = runTest(dispatcher.scheduler) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAction(VaultAction.SubmitUnlockPin("1234"))
        advanceUntilIdle()
        viewModel.onAction(VaultAction.SetBiometricEnabled(true))
        advanceUntilIdle()
        assertTrue(biometricEnabled)

        viewModel.onAction(VaultAction.SetBiometricEnabled(false))
        advanceUntilIdle()
        assertFalse(viewModel.state.value.biometricEnabled)
        assertFalse(biometricEnabled)

        val reopened = createViewModel()
        advanceUntilIdle()
        reopened.onAction(VaultAction.BiometricAuthenticated)
        assertEquals(VaultStage.LOCKED, reopened.state.value.stage)
        reopened.onAction(VaultAction.SubmitUnlockPin("1234"))
        advanceUntilIdle()
        assertEquals(VaultStage.UNLOCKED, reopened.state.value.stage)
    }

    private fun createViewModel(hasPin: CompletableDeferred<Boolean> = CompletableDeferred(true)) = VaultViewModel(
        output = VaultViewModel.Output(navigateUp = {}, playVideo = {}, playVideos = {}),
        vaultRepository = vaultRepository,
        vaultPinRepository = object : VaultPinRepository {
            override suspend fun hasPinSet(): Boolean = hasPin.await()
            override suspend fun setPin(pin: String) = Unit
            override suspend fun isBiometricEnabled(): Boolean = biometricEnabled
            override suspend fun setBiometricEnabled(enabled: Boolean) {
                biometricEnabled = enabled
            }
            override suspend fun verifyPin(pin: String): Boolean = pin == "1234"
            override suspend fun hasShownHideConfirmation(): Boolean = false
            override suspend fun setHideConfirmationShown() = Unit
        },
        getHiddenVideosUseCase = GetHiddenVideosUseCase(vaultRepository, dispatcher),
        preferencesRepository = object : PreferencesRepository {
            override val applicationPreferences = MutableStateFlow(ApplicationPreferences())
            override val playerPreferences = MutableStateFlow(PlayerPreferences())
            override suspend fun updateApplicationPreferences(transform: suspend (ApplicationPreferences) -> ApplicationPreferences) = Unit
            override suspend fun updatePlayerPreferences(transform: suspend (PlayerPreferences) -> PlayerPreferences) = Unit
            override suspend fun resetPreferences() = Unit
        },
    )

    private class FakeVaultRepository : VaultRepository {
        var observations = 0

        override fun observeHiddenVideos(): Flow<List<Video>> {
            observations++
            return flowOf(listOf(Video.sample))
        }

        override suspend fun hideVideos(videos: List<Video>) = Unit
        override suspend fun unhideVideos(videos: List<Video>) = UnhideResult()
        override suspend fun deleteHiddenVideos(videos: List<Video>) = Unit
        override suspend fun getHiddenVideoInfo(id: Long): MediaInfo? = null
    }
}
