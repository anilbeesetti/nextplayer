package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.datastore.datasource.AppPreferencesDataSource
import dev.anilbeesetti.nextplayer.core.datastore.datasource.PlayerPreferencesDataSource
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
import javax.inject.Inject

class LocalPreferencesRepository @Inject constructor(
    private val appPreferencesDataSource: AppPreferencesDataSource,
    private val playerPreferencesDataSource: PlayerPreferencesDataSource
) : PreferencesRepository {
    override val applicationPreferences: Flow<ApplicationPreferences>
        get() = appPreferencesDataSource.preferences

    override val playerPreferences: Flow<PlayerPreferences>
        get() = playerPreferencesDataSource.preferences

    override suspend fun setSortOrder(sortOrder: SortOrder) {
        appPreferencesDataSource.update { it.copy(sortOrder = sortOrder) }
    }

    override suspend fun setSortBy(sortBy: SortBy) {
        appPreferencesDataSource.update { it.copy(sortBy = sortBy) }
    }

    override suspend fun setGroupVideosByFolder(value: Boolean) {
        appPreferencesDataSource.update { it.copy(groupVideosByFolder = value) }
    }

    override suspend fun addToExcludedFolders(folder: String) {
        appPreferencesDataSource.update { it.copy(excludeFolders = it.excludeFolders + folder) }
    }

    override suspend fun removeFromExcludedFolders(folder: String) {
        appPreferencesDataSource.update { it.copy(excludeFolders = it.excludeFolders - folder) }
    }

    override suspend fun setThemeConfig(themeConfig: ThemeConfig) {
        appPreferencesDataSource.update { it.copy(themeConfig = themeConfig) }
    }

    override suspend fun setUseDynamicColors(value: Boolean) {
        appPreferencesDataSource.update { it.copy(useDynamicColors = value) }
    }

    override suspend fun setPlaybackResume(resume: Resume) {
        playerPreferencesDataSource.update { it.copy(resume = resume) }
    }

    override suspend fun shouldRememberPlayerBrightness(value: Boolean) {
        playerPreferencesDataSource.update { it.copy(rememberPlayerBrightness = value) }
    }

    override suspend fun setPlayerBrightness(value: Float) {
        playerPreferencesDataSource.update { it.copy(playerBrightness = value) }
    }

    override suspend fun setDoubleTapGesture(gesture: DoubleTapGesture) {
        playerPreferencesDataSource.update { it.copy(doubleTapGesture = gesture) }
    }

    override suspend fun setFastSeek(seek: FastSeek) {
        playerPreferencesDataSource.update { it.copy(fastSeek = seek) }
    }

    override suspend fun setUseSwipeControls(value: Boolean) {
        playerPreferencesDataSource.update { it.copy(useSwipeControls = value) }
    }

    override suspend fun setUseSeekControls(value: Boolean) {
        playerPreferencesDataSource.update { it.copy(useSeekControls = value) }
    }

    override suspend fun setRememberSelections(value: Boolean) {
        playerPreferencesDataSource.update { it.copy(rememberSelections = value) }
    }

    override suspend fun setPreferredAudioLanguage(value: String) {
        playerPreferencesDataSource.update { it.copy(preferredAudioLanguage = value) }
    }

    override suspend fun setPreferredSubtitleLanguage(value: String) {
        playerPreferencesDataSource.update { it.copy(preferredSubtitleLanguage = value) }
    }

    override suspend fun setPlayerScreenOrientation(value: ScreenOrientation) {
        playerPreferencesDataSource.update { it.copy(playerScreenOrientation = value) }
    }
}
