package dev.anilbeesetti.nextplayer.core.datastore.datasource

import androidx.datastore.core.DataStore
import dev.anilbeesetti.nextplayer.core.datastore.AppPreferences
import dev.anilbeesetti.nextplayer.core.datastore.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.datastore.Resume
import dev.anilbeesetti.nextplayer.core.datastore.SortBy
import dev.anilbeesetti.nextplayer.core.datastore.SortOrder
import javax.inject.Inject
import timber.log.Timber

class PlayerPreferencesDataSource @Inject constructor(
    private val preferencesDataStore: DataStore<PlayerPreferences>
) {

    val preferencesFlow = preferencesDataStore.data

    suspend fun setResume(resume: Resume) {
        try {
            preferencesDataStore.updateData { it.copy(resume = resume) }
        } catch (ioException: Exception) {
            Timber.tag("NextPlayerPreferences").e("Failed to update player preferences: $ioException")
        }
    }
}
