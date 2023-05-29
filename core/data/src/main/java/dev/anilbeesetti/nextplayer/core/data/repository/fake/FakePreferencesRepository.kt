package dev.anilbeesetti.nextplayer.core.data.repository.fake

import dev.anilbeesetti.nextplayer.core.data.mappers.toAppPrefs
import dev.anilbeesetti.nextplayer.core.data.mappers.toPlayerPrefs
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.datastore.AppPreferences
import dev.anilbeesetti.nextplayer.core.datastore.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.AppPrefs
import dev.anilbeesetti.nextplayer.core.model.DoubleTapGesture
import dev.anilbeesetti.nextplayer.core.model.FastSeek
import dev.anilbeesetti.nextplayer.core.model.PlayerPrefs
import dev.anilbeesetti.nextplayer.core.model.Resume
import dev.anilbeesetti.nextplayer.core.model.SortBy
import dev.anilbeesetti.nextplayer.core.model.SortOrder
import dev.anilbeesetti.nextplayer.core.model.ThemeConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakePreferencesRepository : PreferencesRepository {

    private val appPreferences = MutableStateFlow(AppPreferences())
    private val playerPreferences = MutableStateFlow(PlayerPreferences())

    override val appPrefsFlow: Flow<AppPrefs>
        get() = appPreferences.map(AppPreferences::toAppPrefs)
    override val playerPrefsFlow: Flow<PlayerPrefs>
        get() = playerPreferences.map(PlayerPreferences::toPlayerPrefs)

    override suspend fun setSortOrder(sortOrder: SortOrder) {
        appPreferences.update { it.copy(sortOrder = sortOrder) }
    }

    override suspend fun setSortBy(sortBy: SortBy) {
        appPreferences.update { it.copy(sortBy = sortBy) }
    }

    override suspend fun setPlaybackResume(resume: Resume) {
        playerPreferences.update { it.copy(resume = resume) }
    }

    override suspend fun shouldRememberPlayerBrightness(value: Boolean) {
        playerPreferences.update { it.copy(rememberPlayerBrightness = value) }
    }

    override suspend fun setPlayerBrightness(value: Float) {
        playerPreferences.update { it.copy(playerBrightness = value) }
    }

    override suspend fun setDoubleTapGesture(gesture: DoubleTapGesture) {
        playerPreferences.update { it.copy(doubleTapGesture = gesture) }
    }

    override suspend fun setFastSeek(seek: FastSeek) {
        playerPreferences.update { it.copy(fastSeek = seek) }
    }

    override suspend fun setGroupVideosByFolder(value: Boolean) {
        appPreferences.update { it.copy(groupVideosByFolder = value) }
    }

    override suspend fun setUseSwipeControls(value: Boolean) {
        playerPreferences.update { it.copy(useSwipeControls = value) }
    }

    override suspend fun setUseSeekControls(value: Boolean) {
        playerPreferences.update { it.copy(useSeekControls = value) }
    }

    override suspend fun setRememberSelections(value: Boolean) {
        playerPreferences.update { it.copy(rememberSelections = value) }
    }

    override suspend fun setThemeConfig(themeConfig: ThemeConfig) {
        appPreferences.update { it.copy(themeConfig = themeConfig) }
    }

    override suspend fun setUseDynamicColors(value: Boolean) {
        appPreferences.update { it.copy(useDynamicColors = value) }
    }
}
