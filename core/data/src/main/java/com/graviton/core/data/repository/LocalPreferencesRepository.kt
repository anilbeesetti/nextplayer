package com.graviton.core.data.repository

import com.graviton.core.common.di.ApplicationScope
import com.graviton.core.datastore.datasource.AppPreferencesDataSource
import com.graviton.core.datastore.datasource.PlayerPreferencesDataSource
import com.graviton.core.model.ApplicationPreferences
import com.graviton.core.model.PlayerPreferences
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LocalPreferencesRepository @Inject constructor(
    private val appPreferencesDataSource: AppPreferencesDataSource,
    private val playerPreferencesDataSource: PlayerPreferencesDataSource,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : PreferencesRepository {

    override val applicationPreferences: StateFlow<ApplicationPreferences> =
        appPreferencesDataSource.preferences.stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = ApplicationPreferences(),
        )

    override val playerPreferences: StateFlow<PlayerPreferences> =
        playerPreferencesDataSource.preferences.stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = PlayerPreferences(),
        )

    override suspend fun updateApplicationPreferences(
        transform: suspend (ApplicationPreferences) -> ApplicationPreferences,
    ) {
        appPreferencesDataSource.update(transform)
    }

    override suspend fun updatePlayerPreferences(
        transform: suspend (PlayerPreferences) -> PlayerPreferences,
    ) {
        playerPreferencesDataSource.update(transform)
    }

    override suspend fun resetPreferences() {
        appPreferencesDataSource.update { ApplicationPreferences() }
        playerPreferencesDataSource.update { PlayerPreferences() }
    }
}
