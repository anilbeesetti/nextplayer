package com.graviton.settings.screens.appearance

import com.graviton.core.data.repository.fake.FakePreferencesRepository
import com.graviton.settings.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppearancePreferencesGlassUiTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `glass UI is off by default`() = runTest {
        val repository = FakePreferencesRepository()
        val viewModel = AppearancePreferencesViewModel(repository)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.preferences.useGlassUi)
    }

    @Test
    fun `toggling glass UI persists the new value`() = runTest {
        val repository = FakePreferencesRepository()
        val viewModel = AppearancePreferencesViewModel(repository)
        advanceUntilIdle()

        viewModel.onEvent(AppearancePreferencesEvent.ToggleGlassUi)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.preferences.useGlassUi)
        assertTrue(repository.applicationPreferences.value.useGlassUi)

        viewModel.onEvent(AppearancePreferencesEvent.ToggleGlassUi)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.preferences.useGlassUi)
        assertFalse(repository.applicationPreferences.value.useGlassUi)
    }
}
