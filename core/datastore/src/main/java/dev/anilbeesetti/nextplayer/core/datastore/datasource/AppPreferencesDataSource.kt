package dev.anilbeesetti.nextplayer.core.datastore.datasource

import androidx.datastore.core.DataStore
import dev.anilbeesetti.nextplayer.core.datastore.AppPreferences
import dev.anilbeesetti.nextplayer.core.datastore.SortBy
import dev.anilbeesetti.nextplayer.core.datastore.SortOrder
import dev.anilbeesetti.nextplayer.core.datastore.ThemeConfig
import javax.inject.Inject
import timber.log.Timber

class AppPreferencesDataSource @Inject constructor(
    private val appPreferences: DataStore<AppPreferences>
) {

    val preferencesFlow = appPreferences.data

    suspend fun setSortBy(sortBy: SortBy) {
        try {
            appPreferences.updateData { it.copy(sortBy = sortBy) }
        } catch (ioException: Exception) {
            Timber.tag("NextPlayerPreferences").e("Failed to update app preferences: $ioException")
        }
    }

    suspend fun setSortOrder(sortOrder: SortOrder) {
        try {
            appPreferences.updateData { it.copy(sortOrder = sortOrder) }
        } catch (ioException: Exception) {
            Timber.tag("NextPlayerPreferences").e("Failed to update app preferences: $ioException")
        }
    }

    suspend fun setGroupVideosByFolder(groupVideosByFolder: Boolean) {
        try {
            appPreferences.updateData { it.copy(groupVideosByFolder = groupVideosByFolder) }
        } catch (ioException: Exception) {
            Timber.tag("NextPlayerPreferences").e("Failed to update app preferences: $ioException")
        }
    }

    suspend fun setThemeConfig(themeConfig: ThemeConfig) {
        try {
            appPreferences.updateData { it.copy(themeConfig = themeConfig) }
        } catch (ioException: Exception) {
            Timber.tag("NextPlayerPreferences").e("Failed to update app preferences: $ioException")
        }
    }

    suspend fun setUseDynamicColors(value: Boolean) {
        try {
            appPreferences.updateData { it.copy(useDynamicColors = value) }
        } catch (ioException: Exception) {
            Timber.tag("NextPlayerPreferences").e("Failed to update app preferences: $ioException")
        }
    }
}
