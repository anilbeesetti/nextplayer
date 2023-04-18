package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.datastore.AppPreferences
import dev.anilbeesetti.nextplayer.core.datastore.DoubleTapGesture
import dev.anilbeesetti.nextplayer.core.datastore.FastSeek
import dev.anilbeesetti.nextplayer.core.datastore.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.datastore.Resume
import dev.anilbeesetti.nextplayer.core.datastore.SortBy
import dev.anilbeesetti.nextplayer.core.datastore.SortOrder
import dev.anilbeesetti.nextplayer.core.datastore.ThemeConfig
import dev.anilbeesetti.nextplayer.core.datastore.datasource.AppPreferencesDataSource
import dev.anilbeesetti.nextplayer.core.datastore.datasource.PlayerPreferencesDataSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class AppPreferencesRepository @Inject constructor(
    private val preferencesDataSource: AppPreferencesDataSource,
    private val playerPreferencesDataSource: PlayerPreferencesDataSource
) : PreferencesRepository {
    override val appPreferencesFlow: Flow<AppPreferences> =
        preferencesDataSource.preferencesFlow

    override val playerPreferencesFlow: Flow<PlayerPreferences> =
        playerPreferencesDataSource.preferencesFlow

    override suspend fun setSortOrder(sortOrder: SortOrder) {
        preferencesDataSource.setSortOrder(sortOrder)
    }

    override suspend fun setSortBy(sortBy: SortBy) {
        preferencesDataSource.setSortBy(sortBy)
    }

    override suspend fun setPlaybackResume(resume: Resume) {
        playerPreferencesDataSource.setPlaybackResume(resume)
    }

    override suspend fun shouldRememberPlayerBrightness(value: Boolean) {
        playerPreferencesDataSource.shouldRememberPlayerBrightness(value)
    }

    override suspend fun setPlayerBrightness(value: Float) {
        playerPreferencesDataSource.setPlayerBrightness(value)
    }

    override suspend fun setDoubleTapGesture(gesture: DoubleTapGesture) {
        playerPreferencesDataSource.setDoubleTapGesture(gesture)
    }

    override suspend fun setFastSeek(seek: FastSeek) {
        playerPreferencesDataSource.setFastSeek(seek)
    }

    override suspend fun setGroupVideosByFolder(value: Boolean) {
        preferencesDataSource.setGroupVideosByFolder(value)
    }

    override suspend fun setUseSwipeControls(value: Boolean) {
        playerPreferencesDataSource.setUseSwipeControls(value)
    }

    override suspend fun setUseSeekControls(value: Boolean) {
        playerPreferencesDataSource.setUseSeekControls(value)
    }

    override suspend fun setRememberSelections(value: Boolean) {
        playerPreferencesDataSource.setRememberSelections(value)
    }

    override suspend fun setThemeConfig(themeConfig: ThemeConfig) {
        preferencesDataSource.setThemeConfig(themeConfig)
    }

    override suspend fun setUseDynamicColors(value: Boolean) {
        preferencesDataSource.setUseDynamicColors(value)
    }
}
