package dev.anilbeesetti.nextplayer

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.crossfade
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.ThumbnailGenerationStrategy
import okio.FileSystem
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        preferencesRepository: PreferencesRepository,
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(
                    VideoThumbnailDecoder.Factory(
                        thumbnailStrategy = {
                            val preferences = preferencesRepository.applicationPreferences.value
                            when (preferences.thumbnailGenerationStrategy) {
                                ThumbnailGenerationStrategy.FIRST_FRAME -> ThumbnailStrategy.FirstFrame
                                ThumbnailGenerationStrategy.FRAME_AT_PERCENTAGE -> ThumbnailStrategy.FrameAtPercentage(preferences.thumbnailFramePosition)
                                ThumbnailGenerationStrategy.HYBRID -> ThumbnailStrategy.Hybrid(preferences.thumbnailFramePosition)
                            }
                        },
                    ),
                )
            }
            // Memory cache: keep recently viewed thumbnails in RAM for instant re-display on scroll
            .memoryCache(
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25) // use 20% of available app memory
                    .build(),
            )
            .memoryCachePolicy(CachePolicy.ENABLED)
            // Disk cache: cap at 512MB, not 100% of storage
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache(
                DiskCache.Builder()
                    .fileSystem(FileSystem.SYSTEM)
                    .directory(context.filesDir.resolve("thumbnails"))
                    .maxSizeBytes(512L * 1024 * 1024) // 512 MB
                    .build(),
            )
            .crossfade(100)
            .build()
    }
}
