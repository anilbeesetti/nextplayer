package dev.anilbeesetti.nextplayer.core.database

import androidx.room.testing.MigrationTestHelper
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
        InstrumentationRegistry.getInstrumentation(),
        MediaDatabase::class.java,
    )

    @Test
    fun migrationPreservesMediaStateAndCreatesPlaylistTables() {
        helper.createDatabase(TEST_DATABASE, 7).apply {
            execSQL(
                """
                INSERT INTO media_state (
                    uri, playback_position, external_subs, video_scale, subtitle_delay, subtitle_speed
                ) VALUES ('content://existing', 42, '', 1, 0, 1)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            8,
            true,
            MediaDatabase.MIGRATION_7_8,
        )

        migrated.query("SELECT playback_position FROM media_state WHERE uri = 'content://existing'").use {
            assertEquals(true, it.moveToFirst())
            assertEquals(42L, it.getLong(0))
        }
        migrated.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('playlist', 'playlist_item')",
        ).use {
            val tableNames = buildSet {
                while (it.moveToNext()) add(it.getString(0))
            }
            assertEquals(setOf("playlist", "playlist_item"), tableNames)
        }
        val columns = buildMap<String, Int> {
            migrated.query("PRAGMA table_info(`playlist_item`)").use { cursor ->
                while (cursor.moveToNext()) put(cursor.getString(1), cursor.getInt(3))
            }
        }
        assertEquals(0, columns.getValue("image_url"))
        assertEquals(0, columns.getValue("display_path"))
        migrated.close()
    }

    private companion object {
        const val TEST_DATABASE = "migration-7-8"
    }
}
