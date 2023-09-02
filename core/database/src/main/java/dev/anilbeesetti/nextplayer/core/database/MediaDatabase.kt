package dev.anilbeesetti.nextplayer.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import dev.anilbeesetti.nextplayer.core.database.dao.DirectoryDao
import dev.anilbeesetti.nextplayer.core.database.dao.MediumDao
import dev.anilbeesetti.nextplayer.core.database.entities.DirectoryEntity
import dev.anilbeesetti.nextplayer.core.database.entities.MediumEntity

@Database(
    entities = [
        DirectoryEntity::class, MediumEntity::class
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 3, to = 4)
    ]
)
abstract class MediaDatabase : RoomDatabase() {

    abstract fun mediumDao(): MediumDao

    abstract fun directoryDao(): DirectoryDao

    companion object {
        const val DATABASE_NAME = "media_db"
    }
}
