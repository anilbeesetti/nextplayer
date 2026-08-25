package dev.anilbeesetti.nextplayer.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun migrationPreservesLocalPlaylistOrderAndPlaybackHistory() {
        helper.createDatabase(TEST_DATABASE, 9).apply {
            execSQL("INSERT INTO playlist (id, name, created_at) VALUES (7, 'Movies', 100)")
            execSQL(
                """
                INSERT INTO playlist_item (playlist_id, uri, position, last_played_at)
                VALUES (7, 'content://video/one', 0, 321)
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

        migrated.query(
            """
            SELECT type, source, last_refreshed_at
            FROM playlist
            WHERE id = 7
            """.trimIndent(),
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals("LOCAL", it.getString(0))
            assertNull(it.getString(1))
            assertTrue(it.isNull(2))
        }
        migrated.query(
            """
            SELECT uri, position, title, tvg_logo, duration, group_title, last_played_at
            FROM playlist_item
            WHERE playlist_id = 7
            """.trimIndent(),
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals("content://video/one", it.getString(0))
            assertEquals(0, it.getInt(1))
            assertTrue(it.isNull(2))
            assertTrue(it.isNull(3))
            assertEquals(-1, it.getInt(4))
            assertTrue(it.isNull(5))
            assertEquals(321L, it.getLong(6))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DATABASE = "migration-9-10"
    }
}
