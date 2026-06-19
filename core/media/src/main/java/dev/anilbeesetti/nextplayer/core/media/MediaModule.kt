package dev.anilbeesetti.nextplayer.core.media

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.anilbeesetti.nextplayer.core.media.services.LocalMediaOperationsService
import dev.anilbeesetti.nextplayer.core.media.services.MediaOperationsService
import dev.anilbeesetti.nextplayer.core.media.services.MediaService
import dev.anilbeesetti.nextplayer.core.media.services.MediaStoreMediaService
import dev.anilbeesetti.nextplayer.core.media.sync.LocalMediaSynchronizer
import dev.anilbeesetti.nextplayer.core.media.sync.MediaSynchronizer
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface MediaModule {

    @Binds
    @Singleton
    fun bindsMediaSynchronizer(
        mediaSynchronizer: LocalMediaSynchronizer,
    ): MediaSynchronizer

    @Binds
    @Singleton
    fun bindMediaOperationsService(
        mediaService: LocalMediaOperationsService,
    ): MediaOperationsService

    @Binds
    @Singleton
    fun bindMediaService(
        mediaService: MediaStoreMediaService,
    ): MediaService
}
