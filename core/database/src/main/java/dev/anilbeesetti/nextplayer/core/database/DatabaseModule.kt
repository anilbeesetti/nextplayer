package dev.anilbeesetti.nextplayer.core.database

import android.content.Context
import androidx.room.Room
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module(includes = [DaoModule::class])
class DatabaseModule {

    @Single
    fun provideMediaDatabase(
        context: Context,
    ): MediaDatabase = Room.databaseBuilder(
        context = context,
        klass = MediaDatabase::class.java,
        name = MediaDatabase.DATABASE_NAME,
    ).apply {
        addMigrations(
            MediaDatabase.MIGRATION_1_2,
            MediaDatabase.MIGRATION_2_3,
            MediaDatabase.MIGRATION_3_4,
            MediaDatabase.MIGRATION_4_5,
            MediaDatabase.MIGRATION_5_6,
            MediaDatabase.MIGRATION_6_7,
            MediaDatabase.MIGRATION_7_8,
            MediaDatabase.MIGRATION_8_9,
            MediaDatabase.MIGRATION_9_10,
            MediaDatabase.MIGRATION_10_11,
            MediaDatabase.MIGRATION_11_12,
        )
        fallbackToDestructiveMigration(false)
    }.build()
}
