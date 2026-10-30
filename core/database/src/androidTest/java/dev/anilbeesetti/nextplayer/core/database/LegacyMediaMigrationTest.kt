package dev.anilbeesetti.nextplayer.core.database

import android.database.sqlite.SQLiteException
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyMediaMigrationTest {
    // Original Room exports: 9 from v0.11.1, 9-early from 7cd12140, 10/11 from 44963d67^.
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MediaDatabase::class.java,
    )

    @Test
    fun migrateLegacy9() = migrateLegacy(9)

    @Test
    fun migrateEarlierLegacy9() = migrateLegacy(
        9,
        "9-early",
    )

    @Test
    fun migrateLegacy10() = migrateLegacy(10)

    @Test
    fun migrateLegacy11() = migrateLegacy(11)

    @Test
    fun migrateReleasedLegacy9ToRelease75() = migrateLegacy(
        9,
        target = 12,
    )

    @Test
    fun migrateLegacy10ToRelease75() = migrateLegacy(
        10,
        target = 12,
    )

    @Test
    fun migrateLegacy11ToRelease75() = migrateLegacy(
        11,
        target = 12,
    )

    private fun migrateLegacy(
        version: Int,
        fixture: String = version.toString(),
        target: Int = 13,
    ) {
        val name = "legacy-$fixture-to-$target"
        val schema = InstrumentationRegistry.getInstrumentation().context.assets.open(
            "legacy/$fixture.json",
        )
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        helper.createDatabase(
            name,
            version,
        ).apply {
            listOf(
                "playlist_item",
                "playlist",
                "network_connection",
                "hidden_video",
                "media_state",
            )
                .forEach { execSQL("DROP TABLE IF EXISTS `$it`") }
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                execSQL(
                    entity.getString("createSql").replace(
                        "\${TABLE_NAME}",
                        table,
                    ),
                )
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) {
                    execSQL(
                        indices.getJSONObject(
                            j,
                        ).getString("createSql").replace(
                            "\${TABLE_NAME}",
                            table,
                        ),
                    )
                }
            }
            execSQL(
                "UPDATE room_master_table SET identity_hash = ? WHERE id = 42",
                arrayOf(schema.getString("identityHash")),
            )
            execSQL(
                """
                INSERT INTO media (uri, path, filename, parent_path, last_modified, size, width, height,
                    duration, media_store_id, playback_position,
                    audio_track_index, subtitle_track_index,
                    playback_speed, last_played_time, external_subs)
                VALUES ('content://video/one', '/one', 'one', '/', 1, 2, 3, 4, 90000, 5, 12345,
                    2, 3, 1.5, 67890, 'file:///captions.srt')
                """.trimIndent(),
            )
            if (version != 9) execSQL("UPDATE media SET video_scale = 1.5")
            execSQL(
                """
                INSERT INTO media (uri, path, filename, parent_path, last_modified, size, width,
                    height, duration, media_store_id, playback_position)
                VALUES ('content://video/two', '/two', 'two', '/', 1, 2, 3, 4, 0, 6, 0)
                """.trimIndent(),
            )
            val missingTable = if (version == 9) "playlist" else "media_state"
            val originalSql = when (version) {
                9 -> "ALTER TABLE `playlist` ADD COLUMN `type` TEXT NOT NULL DEFAULT 'LOCAL'"

                10 -> "ALTER TABLE `media_state` ADD COLUMN `duration` INTEGER"

                else ->
                    "ALTER TABLE `media_state` ADD COLUMN `external_audio` " +
                        "TEXT NOT NULL DEFAULT ''"
            }
            val failure = assertThrows(SQLiteException::class.java) { execSQL(originalSql) }
            assertTrue(failure.message.orEmpty().contains("no such table: $missingTable"))
            close()
        }
        helper.runMigrationsAndValidate(
            name,
            target,
            false,
            MediaDatabase.MIGRATION_9_10,
            MediaDatabase.MIGRATION_10_11,
            MediaDatabase.MIGRATION_11_12,
            MediaDatabase.MIGRATION_12_13,
        ).use { db ->
            db.query(
                "SELECT playback_position, audio_track_index, subtitle_track_index, " +
                    "last_played_time, duration, external_subs, video_scale, subtitle_delay, " +
                    "subtitle_speed, external_audio FROM media_state WHERE uri = 'content://video/one'",
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(
                    12345L,
                    it.getLong(0),
                )
                assertEquals(
                    2,
                    it.getInt(1),
                )
                assertEquals(
                    3,
                    it.getInt(2),
                )
                assertEquals(
                    67890L,
                    it.getLong(3),
                )
                assertEquals(
                    90000L,
                    it.getLong(4),
                )
                assertEquals(
                    "file:///captions.srt",
                    it.getString(5),
                )
                assertEquals(
                    if (version == 9) 1f else 1.5f,
                    it.getFloat(6),
                )
                assertEquals(
                    0L,
                    it.getLong(7),
                )
                assertEquals(
                    1f,
                    it.getFloat(8),
                )
                assertEquals(
                    "",
                    it.getString(9),
                )
            }
            db.query("SELECT playback_speed FROM media WHERE uri = 'content://video/one'").use {
                assertTrue(it.moveToFirst())
                assertEquals(
                    1.5f,
                    it.getFloat(0),
                )
            }
            if (target == 12) {
                db.query(
                    "SELECT playback_speed FROM media_state WHERE uri = 'content://video/one'",
                ).use {
                    assertTrue(it.moveToFirst())
                    assertEquals(
                        1.5f,
                        it.getFloat(0),
                    )
                }
            }
            db.query(
                "SELECT audio_track_index, subtitle_track_index, last_played_time, " +
                    "external_subs, video_scale FROM media_state WHERE uri = 'content://video/two'",
            ).use {
                assertTrue(it.moveToFirst())
                assertTrue(it.isNull(0))
                assertTrue(it.isNull(1))
                assertTrue(it.isNull(2))
                assertEquals(
                    "",
                    it.getString(3),
                )
                assertEquals(
                    1f,
                    it.getFloat(4),
                )
            }
        }
    }
}
