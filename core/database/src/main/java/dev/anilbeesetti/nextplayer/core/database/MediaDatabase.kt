package dev.anilbeesetti.nextplayer.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.anilbeesetti.nextplayer.core.database.dao.DirectoryDao
import dev.anilbeesetti.nextplayer.core.database.dao.MediumDao
import dev.anilbeesetti.nextplayer.core.database.entities.AudioStreamInfoEntity
import dev.anilbeesetti.nextplayer.core.database.entities.DirectoryEntity
import dev.anilbeesetti.nextplayer.core.database.entities.MediumEntity
import dev.anilbeesetti.nextplayer.core.database.entities.SubtitleStreamInfoEntity
import dev.anilbeesetti.nextplayer.core.database.entities.VideoStreamInfoEntity

@Database(
    entities = [
        DirectoryEntity::class,
        MediumEntity::class,
        VideoStreamInfoEntity::class,
        AudioStreamInfoEntity::class,
        SubtitleStreamInfoEntity::class,
    ],
    version = 9,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
    ],
)
abstract class MediaDatabase : RoomDatabase() {

    abstract fun mediumDao(): MediumDao

    abstract fun directoryDao(): DirectoryDao

    companion object {
        const val DATABASE_NAME = "media_db"

        const val VIDEO_STREAM_INFO_TABLE_SQL = """
                    CREATE TABLE IF NOT EXISTS video_stream_info (
                        stream_index INTEGER NOT NULL,
                        title TEXT,
                        codec_name TEXT NOT NULL,
                        language TEXT,
                        disposition INTEGER NOT NULL,
                        bit_rate INTEGER NOT NULL,
                        frame_rate REAL NOT NULL,
                        width INTEGER NOT NULL,
                        height INTEGER NOT NULL,
                        medium_path TEXT NOT NULL,
                        PRIMARY KEY (medium_path, stream_index),
                        FOREIGN KEY (medium_path) REFERENCES media (path) ON DELETE CASCADE
                    );
                    """

        const val AUDIO_STREAM_INFO_TABLE_SQL = """
                    CREATE TABLE IF NOT EXISTS audio_stream_info (
                        stream_index INTEGER NOT NULL,
                        title TEXT,
                        codec_name TEXT NOT NULL,
                        language TEXT,
                        disposition INTEGER NOT NULL,
                        bit_rate INTEGER NOT NULL,
                        sample_format TEXT,
                        sample_rate INTEGER NOT NULL,
                        channels INTEGER NOT NULL,
                        channel_layout TEXT,
                        medium_path TEXT NOT NULL,
                        PRIMARY KEY (medium_path, stream_index),
                        FOREIGN KEY (medium_path) REFERENCES media (path) ON DELETE CASCADE
                    );
                    """

        const val SUBTITLE_STREAM_INFO_TABLE_SQL = """
                    CREATE TABLE IF NOT EXISTS subtitle_stream_info (
                        stream_index INTEGER NOT NULL,
                        title TEXT,
                        codec_name TEXT NOT NULL,
                        language TEXT,
                        disposition INTEGER NOT NULL,
                        medium_path TEXT NOT NULL,
                        PRIMARY KEY (medium_path, stream_index),
                        FOREIGN KEY (medium_path) REFERENCES media (path) ON DELETE CASCADE
                    );
                    """

        val migration4To5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(VIDEO_STREAM_INFO_TABLE_SQL.trimIndent())
                db.execSQL(AUDIO_STREAM_INFO_TABLE_SQL.trimIndent())
                db.execSQL(SUBTITLE_STREAM_INFO_TABLE_SQL.trimIndent())
            }
        }
    }
}
