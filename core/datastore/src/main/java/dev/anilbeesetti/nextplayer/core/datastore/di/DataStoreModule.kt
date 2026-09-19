package dev.anilbeesetti.nextplayer.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import dev.anilbeesetti.nextplayer.core.common.di.DiQualifiers
import dev.anilbeesetti.nextplayer.core.common.di.DispatchersModule
import dev.anilbeesetti.nextplayer.core.datastore.serializer.ApplicationPreferencesSerializer
import dev.anilbeesetti.nextplayer.core.datastore.serializer.PlayerPreferencesSerializer
import dev.anilbeesetti.nextplayer.core.datastore.serializer.SearchHistorySerializer
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.SearchHistory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

private const val APP_PREFERENCES_DATASTORE_FILE = "app_preferences.json"
private const val PLAYER_PREFERENCES_DATASTORE_FILE = "player_preferences.json"
private const val SEARCH_HISTORY_DATASTORE_FILE = "search_history.json"

@Module(includes = [DispatchersModule::class])
@ComponentScan("dev.anilbeesetti.nextplayer.core.datastore")
class DataStoreModule {

    @Single
    @Named(DiQualifiers.APP_PREFERENCES)
    fun provideAppPreferencesDataStore(
        context: Context,
        @Named(DiQualifiers.IO_DISPATCHER) ioDispatcher: CoroutineDispatcher,
        @Named(DiQualifiers.APPLICATION_SCOPE) scope: CoroutineScope,
    ): DataStore<ApplicationPreferences> = DataStoreFactory.create(
        serializer = ApplicationPreferencesSerializer,
        scope = CoroutineScope(scope.coroutineContext + ioDispatcher),
        produceFile = { context.dataStoreFile(APP_PREFERENCES_DATASTORE_FILE) },
    )

    @Single
    @Named(DiQualifiers.PLAYER_PREFERENCES)
    fun providePlayerPreferencesDataStore(
        applicationContext: Context,
        @Named(DiQualifiers.IO_DISPATCHER) ioDispatcher: CoroutineDispatcher,
        @Named(DiQualifiers.APPLICATION_SCOPE) scope: CoroutineScope,
    ): DataStore<PlayerPreferences> = DataStoreFactory.create(
        serializer = PlayerPreferencesSerializer,
        scope = CoroutineScope(scope.coroutineContext + ioDispatcher),
        produceFile = { applicationContext.dataStoreFile(PLAYER_PREFERENCES_DATASTORE_FILE) },
    )

    @Single
    @Named(DiQualifiers.SEARCH_HISTORY)
    fun provideSearchHistoryDataStore(
        applicationContext: Context,
        @Named(DiQualifiers.IO_DISPATCHER) ioDispatcher: CoroutineDispatcher,
        @Named(DiQualifiers.APPLICATION_SCOPE) scope: CoroutineScope,
    ): DataStore<SearchHistory> = DataStoreFactory.create(
        serializer = SearchHistorySerializer,
        scope = CoroutineScope(scope.coroutineContext + ioDispatcher),
        produceFile = { applicationContext.dataStoreFile(SEARCH_HISTORY_DATASTORE_FILE) },
    )
}
