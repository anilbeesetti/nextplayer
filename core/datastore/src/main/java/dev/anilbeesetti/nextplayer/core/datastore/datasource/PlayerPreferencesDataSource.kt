package dev.anilbeesetti.nextplayer.core.datastore.datasource

import androidx.datastore.core.DataStore
import dev.anilbeesetti.nextplayer.core.datastore.DoubleTapGesture
import dev.anilbeesetti.nextplayer.core.datastore.FastSeek
import dev.anilbeesetti.nextplayer.core.datastore.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.datastore.Resume
import javax.inject.Inject
import timber.log.Timber

class PlayerPreferencesDataSource @Inject constructor(
    private val preferencesDataStore: DataStore<PlayerPreferences>
) {

    val preferencesFlow = preferencesDataStore.data

    suspend fun setPlaybackResume(resume: Resume) {
        try {
            preferencesDataStore.updateData { it.copy(resume = resume) }
        } catch (ioException: Exception) {
            Timber.tag("NextPlayerPreferences").e(
                "Failed to update player preferences: $ioException"
            )
        }
    }

    suspend fun shouldRememberPlayerBrightness(value: Boolean) {
        try {
            preferencesDataStore.updateData { it.copy(rememberPlayerBrightness = value) }
        } catch (ioException: Exception) {
            Timber.tag("NextPlayerPreferences").e(
                "Failed to update player preferences: $ioException"
            )
        }
    }

    suspend fun setPlayerBrightness(value: Float) {
        try {
            preferencesDataStore.updateData { it.copy(playerBrightness = value) }
        } catch (ioException: Exception) {
            Timber.tag("NextPlayerPreferences").e(
                "Failed to update player preferences: $ioException"
            )
        }
    }

    suspend fun setDoubleTapGesture(value: DoubleTapGesture) {
        try {
            preferencesDataStore.updateData { it.copy(doubleTapGesture = value) }
        } catch (ioException: Exception) {
            Timber.tag("NextPlayerPreferences").e(
                "Failed to update player preferences: $ioException"
            )
        }
    }

    suspend fun setFastSeek(seek: FastSeek) {
        try {
            preferencesDataStore.updateData { it.copy(fastSeek = seek) }
        } catch (ioException: Exception) {
            Timber.tag("NextPlayerPreferences").e(
                "Failed to update player preferences: $ioException"
            )
        }
    }
}
