package dev.anilbeesetti.nextplayer.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun migrationPreservesExistingDataAndCreatesFinalPlaylistTables() {
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
            assertTrue(it.moveToFirst())
            assertEquals(42L, it.getLong(0))
        }
        val columns = migrated.query("PRAGMA table_info(`playlist_item`)").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(1))
            }
        }
        assertEquals(setOf("playlist_id", "uri", "position", "last_played_at"), columns)
        migrated.close()
    }

    private companion object {
        const val TEST_DATABASE = "migration-7-8"
    }
}
