package dev.anilbeesetti.nextplayer.core.database

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.anilbeesetti.nextplayer.core.database.dao.VideoDao

@Module
@InstallIn(SingletonComponent::class)
object DaoModule {

    @Provides
    fun provideVideoDao(db: MediaDatabase): VideoDao = db.videoDao()
}