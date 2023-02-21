package dev.anilbeesetti.nextplayer.core.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.anilbeesetti.nextplayer.core.data.mediasource.LocalMediaSource
import dev.anilbeesetti.nextplayer.core.data.mediasource.MediaSource
import dev.anilbeesetti.nextplayer.core.data.repository.LocalVideoRepository
import dev.anilbeesetti.nextplayer.core.data.repository.VideoRepository

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {

    @Binds
    fun bindsVideoRepository(
        videoRepository: LocalVideoRepository
    ): VideoRepository

    @Binds
    fun bindsMediaSource(
        mediaSource: LocalMediaSource
    ): MediaSource
}
