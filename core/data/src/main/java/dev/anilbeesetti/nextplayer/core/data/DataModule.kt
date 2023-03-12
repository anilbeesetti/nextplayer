package dev.anilbeesetti.nextplayer.core.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.anilbeesetti.nextplayer.core.data.repository.AppPreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.LocalVideoRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.VideoRepository

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {

    @Binds
    fun bindsVideoRepository(
        videoRepository: LocalVideoRepository
    ): VideoRepository

    @Binds
    fun bindsPreferencesRepository(
        preferencesRepository: AppPreferencesRepository
    ): PreferencesRepository
}
