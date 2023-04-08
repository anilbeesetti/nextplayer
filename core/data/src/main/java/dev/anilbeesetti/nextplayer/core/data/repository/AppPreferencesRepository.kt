package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.datastore.AppPreferences
import dev.anilbeesetti.nextplayer.core.datastore.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.datastore.Resume
import dev.anilbeesetti.nextplayer.core.datastore.SortBy
import dev.anilbeesetti.nextplayer.core.datastore.SortOrder
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
        playerPreferencesDataSource.setResume(resume)
    }
}
