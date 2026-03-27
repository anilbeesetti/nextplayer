package dev.anilbeesetti.nextplayer.core.database

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
@Module
@InstallIn(SingletonComponent::class)
object DaoModule {

    @Provides
    fun provideMediumStateDao(db: MediaDatabase) = db.mediumStateDao()
}
