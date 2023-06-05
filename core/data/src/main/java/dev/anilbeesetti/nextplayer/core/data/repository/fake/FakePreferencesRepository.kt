package dev.anilbeesetti.nextplayer.core.data.repository.fake

import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.DoubleTapGesture
import dev.anilbeesetti.nextplayer.core.model.FastSeek
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.Resume
import dev.anilbeesetti.nextplayer.core.model.ScreenOrientation
import dev.anilbeesetti.nextplayer.core.model.SortBy
import dev.anilbeesetti.nextplayer.core.model.SortOrder
import dev.anilbeesetti.nextplayer.core.model.ThemeConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakePreferencesRepository : PreferencesRepository {

    private val applicationPreferencesStateFlow = MutableStateFlow(ApplicationPreferences())
    private val playerPreferencesStateFlow = MutableStateFlow(PlayerPreferences())

    override val applicationPreferences: Flow<ApplicationPreferences>
        get() = applicationPreferencesStateFlow
    override val playerPreferences: Flow<PlayerPreferences>
        get() = playerPreferencesStateFlow

    override suspend fun setSortOrder(sortOrder: SortOrder) {
        applicationPreferencesStateFlow.update { it.copy(sortOrder = sortOrder) }
    }

    override suspend fun setSortBy(sortBy: SortBy) {
        applicationPreferencesStateFlow.update { it.copy(sortBy = sortBy) }
    }

    override suspend fun setPlaybackResume(resume: Resume) {
        this.playerPreferencesStateFlow.update { it.copy(resume = resume) }
    }

    override suspend fun shouldRememberPlayerBrightness(value: Boolean) {
        this.playerPreferencesStateFlow.update { it.copy(rememberPlayerBrightness = value) }
    }

    override suspend fun setPlayerBrightness(value: Float) {
        this.playerPreferencesStateFlow.update { it.copy(playerBrightness = value) }
    }

    override suspend fun setDoubleTapGesture(gesture: DoubleTapGesture) {
        this.playerPreferencesStateFlow.update { it.copy(doubleTapGesture = gesture) }
    }

    override suspend fun setFastSeek(seek: FastSeek) {
        this.playerPreferencesStateFlow.update { it.copy(fastSeek = seek) }
    }

    override suspend fun setGroupVideosByFolder(value: Boolean) {
        applicationPreferencesStateFlow.update { it.copy(groupVideosByFolder = value) }
    }

    override suspend fun addToExcludedFolders(folder: String) {
        applicationPreferencesStateFlow.update { it.copy(excludeFolders = it.excludeFolders + folder) }
    }

    override suspend fun removeFromExcludedFolders(folder: String) {
        applicationPreferencesStateFlow.update { it.copy(excludeFolders = it.excludeFolders - folder) }
    }

    override suspend fun setUseSwipeControls(value: Boolean) {
        this.playerPreferencesStateFlow.update { it.copy(useSwipeControls = value) }
    }

    override suspend fun setUseSeekControls(value: Boolean) {
        this.playerPreferencesStateFlow.update { it.copy(useSeekControls = value) }
    }

    override suspend fun setRememberSelections(value: Boolean) {
        this.playerPreferencesStateFlow.update { it.copy(rememberSelections = value) }
    }

    override suspend fun setPreferredAudioLanguage(value: String) {
        this.playerPreferencesStateFlow.update { it.copy(preferredAudioLanguage = value) }
    }

    override suspend fun setPreferredSubtitleLanguage(value: String) {
        this.playerPreferencesStateFlow.update { it.copy(preferredSubtitleLanguage = value) }
    }

    override suspend fun setThemeConfig(themeConfig: ThemeConfig) {
        applicationPreferencesStateFlow.update { it.copy(themeConfig = themeConfig) }
    }

    override suspend fun setUseDynamicColors(value: Boolean) {
        applicationPreferencesStateFlow.update { it.copy(useDynamicColors = value) }
    }

    override suspend fun setPlayerScreenOrientation(value: ScreenOrientation) {
        this.playerPreferencesStateFlow.update { it.copy(playerScreenOrientation = value) }
    }
}
