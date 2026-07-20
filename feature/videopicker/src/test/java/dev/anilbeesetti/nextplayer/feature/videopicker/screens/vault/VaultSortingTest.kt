package dev.anilbeesetti.nextplayer.feature.videopicker.screens.vault

import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.UnhideResult
import dev.anilbeesetti.nextplayer.core.data.repository.VaultPinRepository
import dev.anilbeesetti.nextplayer.core.data.repository.VaultRepository
import dev.anilbeesetti.nextplayer.core.domain.GetHiddenVideosUseCase
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.MediaInfo
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.Sort
import dev.anilbeesetti.nextplayer.core.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
class VaultSortingTest {

    private val testDispatcher = StandardTestDispatcher()
    private val titleAscending = Sort(by = Sort.By.TITLE, order = Sort.Order.ASCENDING)
    private val sizeAscending = Sort(by = Sort.By.SIZE, order = Sort.Order.ASCENDING)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `unlocked vault initially uses application preference sort`() = runTest(testDispatcher.scheduler) {
        val preferencesRepository = FakePreferencesRepository(
            ApplicationPreferences(
                sortBy = titleAscending.by,
                sortOrder = titleAscending.order,
            ),
        )
        val viewModel = createViewModel(preferencesRepository)

        viewModel.onAction(VaultAction.SubmitUnlockPin("1234"))
        advanceUntilIdle()

        assertEquals(titleAscending, viewModel.uiState.value.sort)
        assertEquals(
            listOf("Alpha.mp4", "Bravo.mp4", "Zebra.mp4"),
            viewModel.uiState.value.hiddenVideos.map { it.nameWithExtension },
        )
    }

    @Test
    fun `updating vault sort reorders videos without updating application preferences`() =
        runTest(testDispatcher.scheduler) {
            val preferencesRepository = FakePreferencesRepository(
                ApplicationPreferences(
                    sortBy = titleAscending.by,
                    sortOrder = titleAscending.order,
                ),
            )
            val viewModel = createViewModel(preferencesRepository)
            viewModel.onAction(VaultAction.SubmitUnlockPin("1234"))
            advanceUntilIdle()

            assertEquals(titleAscending, viewModel.uiState.value.sort)
            assertEquals(
                listOf("Alpha.mp4", "Bravo.mp4", "Zebra.mp4"),
                viewModel.uiState.value.hiddenVideos.map { it.nameWithExtension },
            )

            viewModel.onAction(
                VaultAction.UpdateSort(
                    sizeAscending,
                ),
            )
            advanceUntilIdle()

            assertEquals(sizeAscending, viewModel.uiState.value.sort)
            assertEquals(
                listOf("Zebra.mp4", "Alpha.mp4", "Bravo.mp4"),
                viewModel.uiState.value.hiddenVideos.map { it.nameWithExtension },
            )
            assertEquals(0, preferencesRepository.applicationUpdateCount)
            assertEquals(Sort.By.TITLE, preferencesRepository.applicationPreferences.value.sortBy)
            assertEquals(Sort.Order.ASCENDING, preferencesRepository.applicationPreferences.value.sortOrder)
        }

    @Test
    fun `application preference sort change refreshes unlocked vault before local override`() =
        runTest(testDispatcher.scheduler) {
            val preferencesRepository = FakePreferencesRepository(
                ApplicationPreferences(sortBy = titleAscending.by, sortOrder = titleAscending.order),
            )
            val viewModel = createViewModel(preferencesRepository)
            viewModel.onAction(VaultAction.SubmitUnlockPin("1234"))
            advanceUntilIdle()

            preferencesRepository.emitApplicationPreferences(
                ApplicationPreferences(sortBy = sizeAscending.by, sortOrder = sizeAscending.order),
            )
            advanceUntilIdle()

            assertEquals(sizeAscending, viewModel.uiState.value.sort)
            assertEquals(
                listOf("Zebra.mp4", "Alpha.mp4", "Bravo.mp4"),
                viewModel.uiState.value.hiddenVideos.map { it.nameWithExtension },
            )
        }

    @Test
    fun `application preference change preserves local vault sort after override`() =
        runTest(testDispatcher.scheduler) {
            val preferencesRepository = FakePreferencesRepository(
                ApplicationPreferences(sortBy = titleAscending.by, sortOrder = titleAscending.order),
            )
            val viewModel = createViewModel(preferencesRepository)
            viewModel.onAction(VaultAction.SubmitUnlockPin("1234"))
            advanceUntilIdle()
            viewModel.onAction(VaultAction.UpdateSort(sizeAscending))
            advanceUntilIdle()

            preferencesRepository.emitApplicationPreferences(
                ApplicationPreferences(sortBy = Sort.By.DATE, sortOrder = Sort.Order.DESCENDING),
            )
            advanceUntilIdle()

            assertEquals(Sort.By.DATE, viewModel.uiState.value.preferences.sortBy)
            assertEquals(Sort.Order.DESCENDING, viewModel.uiState.value.preferences.sortOrder)
            assertEquals(sizeAscending, viewModel.uiState.value.sort)
            assertEquals(
                listOf("Zebra.mp4", "Alpha.mp4", "Bravo.mp4"),
                viewModel.uiState.value.hiddenVideos.map { it.nameWithExtension },
            )
        }

    @Test
    fun `selecting the active vault sort preserves it through later preference changes`() =
        runTest(testDispatcher.scheduler) {
            val preferencesRepository = FakePreferencesRepository(
                ApplicationPreferences(sortBy = titleAscending.by, sortOrder = titleAscending.order),
            )
            val viewModel = createViewModel(preferencesRepository)
            viewModel.onAction(VaultAction.SubmitUnlockPin("1234"))
            advanceUntilIdle()

            viewModel.onAction(VaultAction.UpdateSort(titleAscending))
            preferencesRepository.emitApplicationPreferences(
                ApplicationPreferences(sortBy = sizeAscending.by, sortOrder = sizeAscending.order),
            )
            advanceUntilIdle()

            assertEquals(Sort.By.SIZE, viewModel.uiState.value.preferences.sortBy)
            assertEquals(Sort.Order.ASCENDING, viewModel.uiState.value.preferences.sortOrder)
            assertEquals(titleAscending, viewModel.uiState.value.sort)
            assertEquals(
                listOf("Alpha.mp4", "Bravo.mp4", "Zebra.mp4"),
                viewModel.uiState.value.hiddenVideos.map { it.nameWithExtension },
            )
        }

    private fun createViewModel(preferencesRepository: FakePreferencesRepository): VaultViewModel {
        val vaultRepository = FakeVaultRepository(
            listOf(
                Video.sample.copy(
                    id = 1,
                    nameWithExtension = "Zebra.mp4",
                    path = "/vault/Zebra.mp4",
                    uriString = "content://vault/zebra",
                    size = 10,
                    dateModified = 200,
                ),
                Video.sample.copy(
                    id = 2,
                    nameWithExtension = "Alpha.mp4",
                    path = "/vault/Alpha.mp4",
                    uriString = "content://vault/alpha",
                    size = 20,
                    dateModified = 100,
                ),
                Video.sample.copy(
                    id = 3,
                    nameWithExtension = "Bravo.mp4",
                    path = "/vault/Bravo.mp4",
                    uriString = "content://vault/bravo",
                    size = 30,
                    dateModified = 300,
                ),
            ),
        )
        return VaultViewModel(
            vaultRepository = vaultRepository,
            vaultPinRepository = FakeVaultPinRepository,
            getHiddenVideosUseCase = GetHiddenVideosUseCase(vaultRepository, testDispatcher),
            preferencesRepository = preferencesRepository,
        )
    }

    private class FakeVaultRepository(videos: List<Video>) : VaultRepository {
        private val hiddenVideos = MutableStateFlow(videos)

        override fun observeHiddenVideos(): Flow<List<Video>> = hiddenVideos
        override suspend fun hideVideos(videos: List<Video>) = Unit
        override suspend fun unhideVideos(videos: List<Video>) = UnhideResult()
        override suspend fun deleteHiddenVideos(videos: List<Video>) = Unit
        override suspend fun getHiddenVideoInfo(id: Long): MediaInfo? = null
    }

    private data object FakeVaultPinRepository : VaultPinRepository {
        override suspend fun hasPinSet(): Boolean = true
        override suspend fun setPin(pin: String) = Unit
        override suspend fun verifyPin(pin: String): Boolean = true
        override suspend fun hasShownHideConfirmation(): Boolean = false
        override suspend fun setHideConfirmationShown() = Unit
    }

    private class FakePreferencesRepository(initialPreferences: ApplicationPreferences) : PreferencesRepository {
        private val preferences = MutableStateFlow(initialPreferences)
        override val applicationPreferences: StateFlow<ApplicationPreferences> = preferences
        override val playerPreferences: StateFlow<PlayerPreferences> = MutableStateFlow(PlayerPreferences())
        var applicationUpdateCount = 0
            private set

        override suspend fun updateApplicationPreferences(
            transform: suspend (ApplicationPreferences) -> ApplicationPreferences,
        ) {
            applicationUpdateCount++
            preferences.value = transform(preferences.value)
        }

        override suspend fun updatePlayerPreferences(
            transform: suspend (PlayerPreferences) -> PlayerPreferences,
        ) = Unit

        override suspend fun resetPreferences() = Unit

        fun emitApplicationPreferences(value: ApplicationPreferences) {
            preferences.value = value
        }
    }
}
