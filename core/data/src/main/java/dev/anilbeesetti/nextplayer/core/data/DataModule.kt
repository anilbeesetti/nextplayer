package dev.anilbeesetti.nextplayer.core.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.anilbeesetti.nextplayer.core.data.medialibrary.LocalMediaLibrary
import dev.anilbeesetti.nextplayer.core.data.medialibrary.MediaLibrary
import dev.anilbeesetti.nextplayer.core.data.repository.VideoRepository
import dev.anilbeesetti.nextplayer.core.data.repository.VideoRepositoryImpl

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {

    @Binds
    fun bindsVideoRepository(
        videoRepository: VideoRepositoryImpl
    ): VideoRepository

    @Binds
    fun bindsMediaLibrary(
        mediaLibrary: LocalMediaLibrary
    ): MediaLibrary
}
