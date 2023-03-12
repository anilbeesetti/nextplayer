package dev.anilbeesetti.nextplayer.core.media

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.anilbeesetti.nextplayer.core.media.mediasource.LocalMediaSource
import dev.anilbeesetti.nextplayer.core.media.mediasource.MediaSource

@Module
@InstallIn(SingletonComponent::class)
interface MediaSourceModule {

    @Binds
    fun bindsMediaSource(
        mediaSource: LocalMediaSource
    ): MediaSource
}