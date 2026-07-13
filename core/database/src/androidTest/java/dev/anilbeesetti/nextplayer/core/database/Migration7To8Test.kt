package dev.anilbeesetti.nextplayer.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration7To8Test {

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = MediaDatabase::class.java,
    )

    @Test
    fun migrationPreservesStateAndAddsEmptyExternalAudioTracks() {
        helper.createDatabase(TEST_DATABASE, 7).apply {
            insertState()
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            8,
            true,
            MediaDatabase.MIGRATION_7_8,
        ).use { database ->
            database.query("SELECT * FROM `media_state` WHERE `uri` = ?", arrayOf("content://video/1"))
                .use { cursor ->
                    check(cursor.moveToFirst())
                    assertEquals("content://video/1", cursor.getString(cursor.getColumnIndexOrThrow("uri")))
                    assertEquals(1234L, cursor.getLong(cursor.getColumnIndexOrThrow("playback_position")))
                    assertEquals(2, cursor.getInt(cursor.getColumnIndexOrThrow("audio_track_index")))
                    assertEquals(3, cursor.getInt(cursor.getColumnIndexOrThrow("subtitle_track_index")))
                    assertEquals(
                        "content://subtitle/1",
                        cursor.getString(cursor.getColumnIndexOrThrow("external_subs")),
                    )
                    assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("external_audio_tracks")))
                }
        }
    }

    private fun SupportSQLiteDatabase.insertState() {
        execSQL(
            """
            INSERT INTO `media_state` (
                `uri`,
                `playback_position`,
                `audio_track_index`,
                `subtitle_track_index`,
                `external_subs`,
                `video_scale`,
                `subtitle_delay`,
                `subtitle_speed`
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>("content://video/1", 1234L, 2, 3, "content://subtitle/1", 1f, 0L, 1f),
        )
    }

    private companion object {
        const val TEST_DATABASE = "migration-7-8-test"
    }
}
