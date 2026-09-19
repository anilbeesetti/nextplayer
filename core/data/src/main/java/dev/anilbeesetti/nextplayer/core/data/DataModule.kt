package dev.anilbeesetti.nextplayer.core.data

import dev.anilbeesetti.nextplayer.core.data.repository.NetworkConnectionRepository
import dev.anilbeesetti.nextplayer.core.database.DatabaseModule
import dev.anilbeesetti.nextplayer.core.datastore.di.DataStoreModule
import dev.anilbeesetti.nextplayer.core.media.MediaModule
import dev.anilbeesetti.nextplayer.core.media.network.NetworkConnectionResolver
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module(includes = [DatabaseModule::class, DataStoreModule::class, MediaModule::class])
@ComponentScan
class DataModule {
    /** Lets `core:media` resolve a playback uri's connection id without depending on `core:data`. */
    @Single
    fun providesNetworkConnectionResolver(
        repository: NetworkConnectionRepository,
    ): NetworkConnectionResolver = NetworkConnectionResolver { id -> repository.getConnection(id) }
}
