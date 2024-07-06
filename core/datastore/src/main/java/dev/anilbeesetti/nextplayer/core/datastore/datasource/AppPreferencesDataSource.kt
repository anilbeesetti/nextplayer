package dev.anilbeesetti.nextplayer.core.datastore.datasource

import androidx.datastore.core.DataStore
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import javax.inject.Inject
import timber.log.Timber

class AppPreferencesDataSource @Inject constructor(
    private val appPreferences: DataStore<ApplicationPreferences>,
) : PreferencesDataSource<ApplicationPreferences> {

    override val preferences = appPreferences.data

    override suspend fun update(
        transform: suspend (ApplicationPreferences) -> ApplicationPreferences,
    ) {
        try {
            appPreferences.updateData(transform)
        } catch (ioException: Exception) {
            Timber.tag("NextPlayerPreferences").e("Failed to update app preferences: $ioException")
        }
    }
}
