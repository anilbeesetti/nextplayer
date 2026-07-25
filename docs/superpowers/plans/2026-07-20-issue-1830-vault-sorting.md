# Issue 1830 Vault Sorting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Secret Vault videos inherit the app-wide saved sort at session start and immediately reorder when a session-only vault sort is selected.

**Architecture:** Keep sorting in the existing `GetHiddenVideosUseCase` and coordinate it from `VaultViewModel`. The view model records whether the vault has a session override, derives the initial sort from `ApplicationPreferences`, and restarts its existing hidden-video collection only when the effective sort changes while unlocked.

**Tech Stack:** Kotlin, Android ViewModel, StateFlow, coroutines test, JUnit 4, Robolectric, Gradle

## Global Constraints

- Vault sort selections apply only to the current `VaultViewModel` session.
- Before a session override, use `ApplicationPreferences.sortBy` and `sortOrder` from the datastore-backed repository.
- Do not write vault sort selections back to the preferences datastore.
- Preserve the existing `GetHiddenVideosUseCase` API and Compose UI.

---

### Task 1: Reactive Vault Session Sorting

**Files:**
- Create: `feature/videopicker/src/test/java/dev/anilbeesetti/nextplayer/feature/videopicker/screens/vault/VaultSortingTest.kt`
- Modify: `feature/videopicker/src/main/java/dev/anilbeesetti/nextplayer/feature/videopicker/screens/vault/VaultViewModel.kt`

**Interfaces:**
- Consumes: `GetHiddenVideosUseCase.invoke(sort: Sort): Flow<List<Video>>`, `PreferencesRepository.applicationPreferences: StateFlow<ApplicationPreferences>`, and `VaultAction.UpdateSort(sort: Sort)`.
- Produces: `VaultUiState.sort` initialized from app preferences until locally overridden, plus a reordered `VaultUiState.hiddenVideos` after `UpdateSort`.

- [ ] **Step 1: Write the failing regression tests**

Create `VaultSortingTest.kt` with two behavior-focused tests and local fakes:

```kotlin
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
                sortBy = Sort.By.TITLE,
                sortOrder = Sort.Order.ASCENDING,
            ),
        )
        val viewModel = createViewModel(preferencesRepository)

        viewModel.onAction(VaultAction.SubmitUnlockPin("1234"))
        advanceUntilIdle()

        assertEquals(listOf("Alpha.mp4", "Zebra.mp4"), viewModel.uiState.value.hiddenVideos.map { it.nameWithExtension })
    }

    @Test
    fun `updating vault sort reorders videos without updating application preferences`() =
        runTest(testDispatcher.scheduler) {
            val preferencesRepository = FakePreferencesRepository(
                ApplicationPreferences(
                    sortBy = Sort.By.TITLE,
                    sortOrder = Sort.Order.ASCENDING,
                ),
            )
            val viewModel = createViewModel(preferencesRepository)
            viewModel.onAction(VaultAction.SubmitUnlockPin("1234"))
            advanceUntilIdle()

            viewModel.onAction(
                VaultAction.UpdateSort(
                    Sort(by = Sort.By.SIZE, order = Sort.Order.ASCENDING),
                ),
            )
            advanceUntilIdle()

            assertEquals(listOf("Zebra.mp4", "Alpha.mp4"), viewModel.uiState.value.hiddenVideos.map { it.nameWithExtension })
            assertEquals(0, preferencesRepository.applicationUpdateCount)
            assertEquals(Sort.By.TITLE, preferencesRepository.applicationPreferences.value.sortBy)
            assertEquals(Sort.Order.ASCENDING, preferencesRepository.applicationPreferences.value.sortOrder)
        }

    private fun createViewModel(preferencesRepository: FakePreferencesRepository): VaultViewModel {
        val vaultRepository = FakeVaultRepository(
            listOf(
                Video.sample.copy(
                    id = 1,
                    nameWithExtension = "Zebra.mp4",
                    path = "/vault/Zebra.mp4",
                    uriString = "content://vault/zebra",
                    size = 1,
                ),
                Video.sample.copy(
                    id = 2,
                    nameWithExtension = "Alpha.mp4",
                    path = "/vault/Alpha.mp4",
                    uriString = "content://vault/alpha",
                    size = 100,
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
    }
}
```

- [ ] **Step 2: Run the tests and verify RED**

Run:

```bash
./gradlew :feature:videopicker:testDebugUnitTest --tests '*VaultSortingTest'
```

Expected: both tests fail because the vault uses its hard-coded date sort and `UpdateSort` does not restart collection.

- [ ] **Step 3: Implement the minimal screen-logic fix**

In `VaultViewModel.kt`, add a session flag next to `hiddenVideosJob`:

```kotlin
private var hasVaultSortOverride = false
```

Replace the preferences collector body with logic that inherits the app sort until an override exists and refreshes an already-unlocked vault only if the inherited sort changed:

```kotlin
preferencesRepository.applicationPreferences.collect { prefs ->
    val inheritedSort = Sort(by = prefs.sortBy, order = prefs.sortOrder)
    val shouldRefresh = !hasVaultSortOverride &&
        uiStateInternal.value.stage == VaultStage.UNLOCKED &&
        uiStateInternal.value.sort != inheritedSort
    uiStateInternal.update {
        it.copy(
            preferences = prefs,
            sort = if (hasVaultSortOverride) it.sort else inheritedSort,
        )
    }
    if (shouldRefresh) collectHiddenVideos()
}
```

Route `VaultAction.UpdateSort` to a private method:

```kotlin
is VaultAction.UpdateSort -> updateSort(action.sort)
```

Add the method:

```kotlin
private fun updateSort(sort: Sort) {
    hasVaultSortOverride = true
    val shouldRefresh = uiStateInternal.value.stage == VaultStage.UNLOCKED &&
        uiStateInternal.value.sort != sort
    uiStateInternal.update { it.copy(sort = sort) }
    if (shouldRefresh) collectHiddenVideos()
}
```

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run:

```bash
./gradlew :feature:videopicker:testDebugUnitTest --tests '*VaultSortingTest'
```

Expected: `VaultSortingTest` passes with two tests and zero failures.

- [ ] **Step 5: Run feature regression tests and compilation**

Run:

```bash
./gradlew :feature:videopicker:testDebugUnitTest :app:compileDebugKotlin
```

Expected: both Gradle tasks complete with `BUILD SUCCESSFUL` and zero failed tests.

- [ ] **Step 6: Review the diff and commit the fix**

Run:

```bash
git diff --check
git diff -- feature/videopicker/src/main/java/dev/anilbeesetti/nextplayer/feature/videopicker/screens/vault/VaultViewModel.kt feature/videopicker/src/test/java/dev/anilbeesetti/nextplayer/feature/videopicker/screens/vault/VaultSortingTest.kt
git add feature/videopicker/src/main/java/dev/anilbeesetti/nextplayer/feature/videopicker/screens/vault/VaultViewModel.kt feature/videopicker/src/test/java/dev/anilbeesetti/nextplayer/feature/videopicker/screens/vault/VaultSortingTest.kt
git commit -m "fix: apply vault video sorting"
```

Expected: the diff contains only the focused ViewModel behavior and regression tests, and the commit succeeds.
