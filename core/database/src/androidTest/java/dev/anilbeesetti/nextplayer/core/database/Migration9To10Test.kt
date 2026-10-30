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
        helper.createDatabase(
            name = TEST_DATABASE,
            version = 9,
        ).apply {
            execSQL(sql = "INSERT INTO playlist (id, name, created_at) VALUES (7, 'Movies', 100)")
            execSQL(
                """
                INSERT INTO playlist_item (playlist_id, uri, position, last_played_at)
                VALUES (7, 'content://video/one', 0, 321)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            name = TEST_DATABASE,
            version = 10,
            validateDroppedTables = true,
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
            assertEquals(
                "LOCAL",
                it.getString(0),
            )
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
            assertEquals(
                "content://video/one",
                it.getString(0),
            )
            assertEquals(
                0,
                it.getInt(1),
            )
            assertTrue(it.isNull(2))
            assertTrue(it.isNull(3))
            assertEquals(
                -1,
                it.getInt(4),
            )
            assertTrue(it.isNull(5))
            assertEquals(
                321L,
                it.getLong(6),
            )
        }
        migrated.close()
    }

    @Test
    fun modern10To13PreservesPlaylistAndPlaybackState() {
        helper.createDatabase(
            "modern-10",
            10,
        ).apply {
            execSQL(
                "INSERT INTO playlist (id, name, type, source, created_at) VALUES (7, 'TV', 'M3U', 'content://list', 100)",
            )
            execSQL(
                "INSERT INTO playlist_item (playlist_id, uri, position, title, duration) VALUES (7, 'content://video', 0, 'News', 90000)",
            )
            execSQL(
                "INSERT INTO media_state (uri, playback_position, external_subs, video_scale, subtitle_delay, subtitle_speed) VALUES ('content://video', 12345, '', 1, 0, 1)",
            )
            close()
        }
        helper.runMigrationsAndValidate(
            "modern-10",
            13,
            true,
            MediaDatabase.MIGRATION_10_11,
            MediaDatabase.MIGRATION_11_12,
            MediaDatabase.MIGRATION_12_13,
        ).use { db ->
            db.query("SELECT type, source FROM playlist WHERE id = 7").use {
                assertTrue(it.moveToFirst())
                assertEquals(
                    "M3U",
                    it.getString(0),
                )
                assertEquals(
                    "content://list",
                    it.getString(1),
                )
            }
            db.query("SELECT title, duration FROM playlist_item WHERE playlist_id = 7").use {
                assertTrue(it.moveToFirst())
                assertEquals(
                    "News",
                    it.getString(0),
                )
                assertEquals(
                    90000L,
                    it.getLong(1),
                )
            }
            db.query(
                "SELECT playback_position, duration FROM media_state WHERE uri = 'content://video'",
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(
                    12345L,
                    it.getLong(0),
                )
                assertTrue(it.isNull(1))
            }
        }
    }

    private companion object {
        const val TEST_DATABASE = "migration-9-10"
    }
}
