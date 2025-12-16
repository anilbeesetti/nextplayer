package dev.anilbeesetti.nextplayer.core.datastore.datasource

import androidx.datastore.core.DataStore
import dev.anilbeesetti.nextplayer.core.common.logging.NextLogger
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import javax.inject.Inject

class PlayerPreferencesDataSource @Inject constructor(
    private val preferencesDataStore: DataStore<PlayerPreferences>,
) : PreferencesDataSource<PlayerPreferences> {

    override val preferences = preferencesDataStore.data

    override suspend fun update(transform: suspend (PlayerPreferences) -> PlayerPreferences) {
        try {
            preferencesDataStore.updateData(transform)
        } catch (ioException: Exception) {
            NextLogger.e("NextPlayerPreferences", "Failed to update player preferences", ioException)
        }
    }
}
