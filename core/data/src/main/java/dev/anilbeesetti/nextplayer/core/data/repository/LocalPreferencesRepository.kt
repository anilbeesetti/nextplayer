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

class LocalPreferencesRepository @Inject constructor(
    private val appPreferencesDataSource: AppPreferencesDataSource,
    private val playerPreferencesDataSource: PlayerPreferencesDataSource
) : PreferencesRepository {
    override val appPreferencesFlow: Flow<AppPreferences> =
        appPreferencesDataSource.preferencesFlow

    override val playerPreferencesFlow: Flow<PlayerPreferences> =
        playerPreferencesDataSource.preferencesFlow

    override suspend fun setSortOrder(sortOrder: SortOrder) {
        appPreferencesDataSource.updateData { it.copy(sortOrder = sortOrder) }
    }

    override suspend fun setSortBy(sortBy: SortBy) {
        appPreferencesDataSource.updateData { it.copy(sortBy = sortBy) }
    }

    override suspend fun setGroupVideosByFolder(value: Boolean) {
        appPreferencesDataSource.updateData { it.copy(groupVideosByFolder = value) }
    }

    override suspend fun setThemeConfig(themeConfig: ThemeConfig) {
        appPreferencesDataSource.updateData { it.copy(themeConfig = themeConfig) }
    }

    override suspend fun setUseDynamicColors(value: Boolean) {
        appPreferencesDataSource.updateData { it.copy(useDynamicColors = value) }
    }

    override suspend fun setPlaybackResume(resume: Resume) {
        playerPreferencesDataSource.updateData { it.copy(resume = resume) }
    }

    override suspend fun shouldRememberPlayerBrightness(value: Boolean) {
        playerPreferencesDataSource.updateData { it.copy(rememberPlayerBrightness = value) }
    }

    override suspend fun setPlayerBrightness(value: Float) {
        playerPreferencesDataSource.updateData { it.copy(playerBrightness = value) }
    }

    override suspend fun setDoubleTapGesture(gesture: DoubleTapGesture) {
        playerPreferencesDataSource.updateData { it.copy(doubleTapGesture = gesture) }
    }

    override suspend fun setFastSeek(seek: FastSeek) {
        playerPreferencesDataSource.updateData { it.copy(fastSeek = seek) }
    }

    override suspend fun setUseSwipeControls(value: Boolean) {
        playerPreferencesDataSource.updateData { it.copy(useSwipeControls = value) }
    }

    override suspend fun setUseSeekControls(value: Boolean) {
        playerPreferencesDataSource.updateData { it.copy(useSeekControls = value) }
    }

    override suspend fun setRememberSelections(value: Boolean) {
        playerPreferencesDataSource.updateData { it.copy(rememberSelections = value) }
    }
}
