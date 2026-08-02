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
class Migration8To9Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MediaDatabase::class.java,
    )

    @Test
    fun migrationAddsNullableLastPlayedUriAndPreservesPlaylistItems() {
        helper.createDatabase(TEST_DATABASE, 8).apply {
            execSQL(
                """
                INSERT INTO playlist (id, name, created_at)
                VALUES (7, 'Movies', 123)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO playlist_item (playlist_id, uri, position)
                VALUES (7, 'content://one', 0)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            9,
            true,
            MediaDatabase.MIGRATION_8_9,
        )

        migrated.query("SELECT last_played_uri FROM playlist WHERE id = 7").use {
            assertTrue(it.moveToFirst())
            assertTrue(it.isNull(0))
        }
        migrated.query("SELECT uri FROM playlist_item WHERE playlist_id = 7").use {
            assertTrue(it.moveToFirst())
            assertEquals("content://one", it.getString(0))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DATABASE = "migration-8-9"
    }
}
