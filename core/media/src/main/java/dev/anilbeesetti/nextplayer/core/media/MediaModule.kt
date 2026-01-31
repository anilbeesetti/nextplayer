package dev.anilbeesetti.nextplayer.core.media

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.request.CachePolicy
import coil3.request.crossfade
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.anilbeesetti.nextplayer.core.media.services.LocalMediaService
import dev.anilbeesetti.nextplayer.core.media.services.MediaService
import dev.anilbeesetti.nextplayer.core.media.sync.LocalMediaInfoSynchronizer
import dev.anilbeesetti.nextplayer.core.media.sync.LocalMediaSynchronizer
import dev.anilbeesetti.nextplayer.core.media.sync.MediaInfoSynchronizer
import dev.anilbeesetti.nextplayer.core.media.sync.MediaSynchronizer
import javax.inject.Singleton
import okio.FileSystem

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
    fun bindsMediaInfoSynchronizer(
        mediaInfoSynchronizer: LocalMediaInfoSynchronizer,
    ): MediaInfoSynchronizer

    @Binds
    @Singleton
    fun bindMediaService(
        mediaService: LocalMediaService,
    ): MediaService
}

@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(VideoThumbnailDecoder.Factory())
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache(
                DiskCache.Builder()
                    .fileSystem(FileSystem.SYSTEM)
                    .directory(context.filesDir.resolve("thumbnails"))
                    .maxSizePercent(1.0)
                    .build(),
            )
            .crossfade(true)
            .build()
    }
}
