package dev.anilbeesetti.nextplayer.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration9To10Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MediaDatabase::class.java,
    )

    @Test
    fun migrationMovesLastPlayedStateOntoTheMatchingPlaylistItem() {
        helper.createDatabase(TEST_DATABASE, 9).apply {
            execSQL(
                """
                INSERT INTO playlist (id, name, created_at, last_played_uri)
                VALUES (7, 'Movies', 123, 'content://two')
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO playlist_item (playlist_id, uri, position)
                VALUES
                    (7, 'content://one', 0),
                    (7, 'content://two', 1)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            10,
            true,
            MediaDatabase.MIGRATION_9_10,
        )

        migrated.query("PRAGMA table_info(playlist)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val columns = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
            assertFalse("last_played_uri" in columns)
        }
        migrated.query(
            """
            SELECT uri, last_played_at
            FROM playlist_item
            WHERE playlist_id = 7
            ORDER BY position
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("content://one", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.moveToNext())
            assertEquals("content://two", cursor.getString(0))
            assertEquals(123L, cursor.getLong(1))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DATABASE = "migration-9-10"
    }
}
