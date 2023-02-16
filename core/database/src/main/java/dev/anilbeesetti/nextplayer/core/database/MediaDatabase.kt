package dev.anilbeesetti.nextplayer.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.anilbeesetti.nextplayer.core.database.dao.VideoDao
import dev.anilbeesetti.nextplayer.core.database.entities.VideoEntity

@Database(
    entities = [
        VideoEntity::class
    ],
    version = 1
)
abstract class MediaDatabase : RoomDatabase() {

    abstract fun videoDao(): VideoDao

    companion object {
        const val DATABASE_NAME = "media_db"
    }
}
