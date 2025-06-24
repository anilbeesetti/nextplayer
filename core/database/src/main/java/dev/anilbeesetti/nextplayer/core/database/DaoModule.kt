package dev.anilbeesetti.nextplayer.core.database

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.anilbeesetti.nextplayer.core.database.dao.DirectoryDao
import dev.anilbeesetti.nextplayer.core.database.dao.MediumDao
import dev.anilbeesetti.nextplayer.core.database.dao.WebDavServerDao

@Module
@InstallIn(SingletonComponent::class)
object DaoModule {

    @Provides
    fun provideMediumDao(db: MediaDatabase): MediumDao = db.mediumDao()

    @Provides
    fun provideDirectoryDao(db: MediaDatabase): DirectoryDao = db.directoryDao()

    @Provides
    fun provideWebDavServerDao(db: MediaDatabase): WebDavServerDao = db.webDavServerDao()
}
