package dev.anilbeesetti.nextplayer.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.anilbeesetti.nextplayer.core.datastore.AppPreferences
import dev.anilbeesetti.nextplayer.core.datastore.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.datastore.serializer.AppPreferencesSerializer
import dev.anilbeesetti.nextplayer.core.datastore.serializer.PlayerPreferencesSerializer
import javax.inject.Singleton

private const val APP_PREFERENCES_DATASTORE_FILE = "app_preferences.json"
private const val PLAYER_PREFERENCES_DATASTORE_FILE = "player_preferences.json"

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideAppPreferencesDataStore(
        @ApplicationContext applicationContext: Context
    ): DataStore<AppPreferences> {
        return DataStoreFactory.create(
            serializer = AppPreferencesSerializer,
            produceFile = { applicationContext.dataStoreFile(APP_PREFERENCES_DATASTORE_FILE) }
        )
    }

    @Provides
    @Singleton
    fun providePlayerPreferencesDataStore(
        @ApplicationContext applicationContext: Context
    ): DataStore<PlayerPreferences> {
        return DataStoreFactory.create(
            serializer = PlayerPreferencesSerializer,
            produceFile = { applicationContext.dataStoreFile(PLAYER_PREFERENCES_DATASTORE_FILE) }
        )
    }
}
