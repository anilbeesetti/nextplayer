package dev.anilbeesetti.nextplayer.core.common.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.anilbeesetti.nextplayer.core.common.services.RealSystemService
import dev.anilbeesetti.nextplayer.core.common.services.SystemService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface ServicesModule {

    @Binds
    @Singleton
    fun providesSystemService(
        systemService: RealSystemService,
    ): SystemService
}