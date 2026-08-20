package com.graviton.core.media

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.graviton.core.media.network.DefaultNetworkClientFactory
import com.graviton.core.media.network.NetworkClientFactory
import com.graviton.core.media.network.keys.DefaultSshKeyStore
import com.graviton.core.media.network.keys.SshKeyStore
import com.graviton.core.media.services.LocalMediaOperationsService
import com.graviton.core.media.services.MediaOperationsService
import com.graviton.core.media.services.MediaService
import com.graviton.core.media.services.MediaStoreMediaService
import com.graviton.core.media.sync.LocalMediaSynchronizer
import com.graviton.core.media.sync.MediaSynchronizer
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface MediaModule {

    @Binds
    @Singleton
    fun bindNetworkClientFactory(factory: DefaultNetworkClientFactory): NetworkClientFactory

    @Binds
    @Singleton
    fun bindSshKeyStore(store: DefaultSshKeyStore): SshKeyStore

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
