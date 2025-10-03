package dev.anilbeesetti.nextplayer.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.anilbeesetti.nextplayer.core.database.dao.DirectoryDao
import dev.anilbeesetti.nextplayer.core.database.dao.MediumDao
import dev.anilbeesetti.nextplayer.core.database.dao.MediumStateDao
import dev.anilbeesetti.nextplayer.core.database.entities.AudioStreamInfoEntity
import dev.anilbeesetti.nextplayer.core.database.entities.DirectoryEntity
import dev.anilbeesetti.nextplayer.core.database.entities.MediumEntity
import dev.anilbeesetti.nextplayer.core.database.entities.MediumStateEntity
import dev.anilbeesetti.nextplayer.core.database.entities.SubtitleStreamInfoEntity
import dev.anilbeesetti.nextplayer.core.database.entities.VideoStreamInfoEntity

@Database(
    entities = [
        DirectoryEntity::class,
        MediumEntity::class,
        MediumStateEntity::class,
        VideoStreamInfoEntity::class,
        AudioStreamInfoEntity::class,
        SubtitleStreamInfoEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class MediaDatabase : RoomDatabase() {

    abstract fun mediumDao(): MediumDao

    abstract fun mediumStateDao(): MediumStateDao

    abstract fun directoryDao(): DirectoryDao

    companion object {
        const val DATABASE_NAME = "media_db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create the new media_state table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `media_state` (
                        `uri` TEXT NOT NULL, 
                        `playback_position` INTEGER NOT NULL DEFAULT 0, 
                        `audio_track_index` INTEGER, 
                        `subtitle_track_index` INTEGER, 
                        `playback_speed` REAL, 
                        `last_played_time` INTEGER, 
                        `external_subs` TEXT NOT NULL DEFAULT '', 
                        `video_scale` REAL NOT NULL DEFAULT 1, 
                        PRIMARY KEY(`uri`)
                    )
                    """,
                )

                // Create index for the uri column
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_media_state_uri` ON `media_state` (`uri`)
                    """,
                )

                // Copy data from media table to media_state table
                db.execSQL(
                    """
                    INSERT INTO `media_state` (
                        `uri`, 
                        `playback_position`, 
                        `audio_track_index`, 
                        `subtitle_track_index`, 
                        `playback_speed`, 
                        `last_played_time`, 
                        `external_subs`, 
                        `video_scale`
                    ) 
                    SELECT 
                        `uri`, 
                        `playback_position`, 
                        `audio_track_index`, 
                        `subtitle_track_index`, 
                        `playback_speed`, 
                        `last_played_time`, 
                        `external_subs`, 
                        `video_scale` 
                    FROM `media`
                    """,
                )

                // Create a temporary table for the new media schema
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `media_new` (
                        `uri` TEXT NOT NULL, 
                        `path` TEXT NOT NULL, 
                        `filename` TEXT NOT NULL, 
                        `parent_path` TEXT NOT NULL, 
                        `last_modified` INTEGER NOT NULL, 
                        `size` INTEGER NOT NULL, 
                        `width` INTEGER NOT NULL, 
                        `height` INTEGER NOT NULL, 
                        `duration` INTEGER NOT NULL, 
                        `media_store_id` INTEGER NOT NULL, 
                        `format` TEXT, 
                        `thumbnail_path` TEXT, 
                        PRIMARY KEY(`uri`)
                    )
                    """,
                )

                // Copy data from the old media table to the new one
                db.execSQL(
                    """
                    INSERT INTO `media_new` (
                        `uri`, 
                        `path`, 
                        `filename`, 
                        `parent_path`, 
                        `last_modified`, 
                        `size`, 
                        `width`, 
                        `height`, 
                        `duration`, 
                        `media_store_id`, 
                        `format`, 
                        `thumbnail_path`
                    ) 
                    SELECT 
                        `uri`, 
                        `path`, 
                        `filename`, 
                        `parent_path`, 
                        `last_modified`, 
                        `size`, 
                        `width`, 
                        `height`, 
                        `duration`, 
                        `media_store_id`, 
                        `format`, 
                        `thumbnail_path` 
                    FROM `media`
                    """,
                )

                // Drop the old media table
                db.execSQL("DROP TABLE `media`")

                // Rename the new media table to media
                db.execSQL("ALTER TABLE `media_new` RENAME TO `media`")

                // Recreate the indices for the media table
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_media_uri` ON `media` (`uri`)
                    """,
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_media_path` ON `media` (`path`)
                    """,
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop the unique index on path
                db.execSQL("DROP INDEX IF EXISTS `index_media_path`")

                // Recreate the index without unique constraint
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_media_path` ON `media` (`path`)
                    """,
                )
            }
        }
    }
}
