package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.datastore.AppPreferences
import dev.anilbeesetti.nextplayer.core.datastore.SortBy
import dev.anilbeesetti.nextplayer.core.datastore.SortOrder
import dev.anilbeesetti.nextplayer.core.datastore.datasource.AppPreferencesDataSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AppPreferencesRepository @Inject constructor(
    private val preferencesDataSource: AppPreferencesDataSource
): PreferencesRepository {
    override val preferences: Flow<AppPreferences>
        get() = preferencesDataSource.preferencesFlow

    override suspend fun setSortOrder(sortOrder: SortOrder) {
        preferencesDataSource.setSortOrder(sortOrder)
    }

    override suspend fun setSortBy(sortBy: SortBy) {
        preferencesDataSource.setSortBy(sortBy)
    }
}