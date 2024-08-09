package dev.anilbeesetti.nextplayer.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Singleton
    @Provides
    fun provideMediaDatabase(
        @ApplicationContext context: Context,
    ): MediaDatabase = Room.databaseBuilder(
        context = context,
        klass = MediaDatabase::class.java,
        name = MediaDatabase.DATABASE_NAME,
    ).fallbackToDestructiveMigration().build()
}
