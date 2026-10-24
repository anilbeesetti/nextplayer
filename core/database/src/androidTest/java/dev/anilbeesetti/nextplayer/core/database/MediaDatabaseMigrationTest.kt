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
class MediaDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MediaDatabase::class.java,
    )

    @Test
    fun migrate8To9_preservesConnectionAndAddsPasswordDefaults() {
        helper.createDatabase(
            name = TEST_DB,
            version = 8,
        ).apply {
            execSQL(
                sql = "INSERT INTO network_connection " +
                    "(id,name,protocol,host,port,path,username,password,use_https,created_at) " +
                    "VALUES (1,'NAS','FTP','10.0.2.2',2121,'/media','alice','secret',0,123)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            name = TEST_DB,
            version = 9,
            validateDroppedTables = true,
            MediaDatabase.MIGRATION_8_9,
        ).use { db ->
            db.query(query = "SELECT * FROM network_connection WHERE id = 1").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(
                    1L,
                    cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                )
                assertEquals(
                    "NAS",
                    cursor.getString(cursor.getColumnIndexOrThrow("name")),
                )
                assertEquals(
                    "FTP",
                    cursor.getString(cursor.getColumnIndexOrThrow("protocol")),
                )
                assertEquals(
                    "10.0.2.2",
                    cursor.getString(cursor.getColumnIndexOrThrow("host")),
                )
                assertEquals(
                    2121,
                    cursor.getInt(cursor.getColumnIndexOrThrow("port")),
                )
                assertEquals(
                    "/media",
                    cursor.getString(cursor.getColumnIndexOrThrow("path")),
                )
                assertEquals(
                    "alice",
                    cursor.getString(cursor.getColumnIndexOrThrow("username")),
                )
                assertEquals(
                    "secret",
                    cursor.getString(cursor.getColumnIndexOrThrow("password")),
                )
                assertEquals(
                    0,
                    cursor.getInt(cursor.getColumnIndexOrThrow("use_https")),
                )
                assertEquals(
                    123L,
                    cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                )
                assertEquals(
                    "PASSWORD",
                    cursor.getString(cursor.getColumnIndexOrThrow("authentication")),
                )
                assertEquals(
                    "",
                    cursor.getString(cursor.getColumnIndexOrThrow("private_key_file_name")),
                )
                assertEquals(
                    "",
                    cursor.getString(cursor.getColumnIndexOrThrow("private_key_passphrase")),
                )
                assertEquals(
                    "",
                    cursor.getString(cursor.getColumnIndexOrThrow("host_key_fingerprint")),
                )
            }
        }
    }

    @Test
    fun migrate11To12_preservesPlaybackAndAddsEmptyAudio() {
        helper.createDatabase(
            name = "audio-migration",
            version = 11,
        ).apply {
            execSQL(
                sql = "INSERT INTO media_state " +
                    "(uri, playback_position, external_subs, " +
                    "video_scale, subtitle_delay, subtitle_speed) " +
                    "VALUES ('content://video/one', 12345, 'file:///captions.srt', 1, 0, 1)",
            )
            close()
        }
        helper.runMigrationsAndValidate(
            name = "audio-migration",
            version = 12,
            validateDroppedTables = true,
            MediaDatabase.MIGRATION_11_12,
        ).use { db ->
            db.query(
                query = "SELECT playback_position, external_subs, external_audio FROM media_state",
            ).use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(
                    12345L,
                    cursor.getLong(0),
                )
                assertEquals(
                    "file:///captions.srt",
                    cursor.getString(1),
                )
                assertEquals(
                    "",
                    cursor.getString(2),
                )
            }
        }
    }

    @Test
    fun migrate12To13_removesVideoSpeedAndPreservesOtherState() {
        helper.createDatabase(
            name = "speed-migration",
            version = 12,
        ).apply {
            execSQL(
                """
                INSERT INTO media_state (
                    uri, playback_position, audio_track_index, subtitle_track_index, playback_speed,
                    last_played_time, duration, external_subs, external_audio, video_scale,
                    subtitle_delay, subtitle_speed
                ) VALUES (
                    'content://video/one', 12345, 2, 3, 1.75, 67890, 90000,
                    'file:///captions.srt', 'file:///audio.aac', 1.5, 250, 0.75
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO media_state (
                    uri, playback_position, external_subs, video_scale, subtitle_delay, subtitle_speed
                ) VALUES ('content://video/two', 0, '', 1, 0, 1)
                """.trimIndent(),
            )
            close()
        }
        helper.runMigrationsAndValidate(
            name = "speed-migration",
            version = 13,
            validateDroppedTables = true,
            MediaDatabase.MIGRATION_12_13,
        ).use { db ->
            db.query(query = "SELECT * FROM media_state ORDER BY uri").use { cursor ->
                assertEquals(
                    2,
                    cursor.count,
                )
                assertEquals(
                    -1,
                    cursor.getColumnIndex("playback_speed"),
                )
                check(cursor.moveToFirst())
                assertEquals(
                    "content://video/one",
                    cursor.getString(cursor.getColumnIndexOrThrow("uri")),
                )
                assertEquals(
                    12345L,
                    cursor.getLong(cursor.getColumnIndexOrThrow("playback_position")),
                )
                assertEquals(
                    2,
                    cursor.getInt(cursor.getColumnIndexOrThrow("audio_track_index")),
                )
                assertEquals(
                    3,
                    cursor.getInt(cursor.getColumnIndexOrThrow("subtitle_track_index")),
                )
                assertEquals(
                    67890L,
                    cursor.getLong(cursor.getColumnIndexOrThrow("last_played_time")),
                )
                assertEquals(
                    90000L,
                    cursor.getLong(cursor.getColumnIndexOrThrow("duration")),
                )
                assertEquals(
                    "file:///captions.srt",
                    cursor.getString(cursor.getColumnIndexOrThrow("external_subs")),
                )
                assertEquals(
                    "file:///audio.aac",
                    cursor.getString(cursor.getColumnIndexOrThrow("external_audio")),
                )
                assertEquals(
                    1.5f,
                    cursor.getFloat(cursor.getColumnIndexOrThrow("video_scale")),
                )
                assertEquals(
                    250L,
                    cursor.getLong(cursor.getColumnIndexOrThrow("subtitle_delay")),
                )
                assertEquals(
                    0.75f,
                    cursor.getFloat(cursor.getColumnIndexOrThrow("subtitle_speed")),
                )
                check(cursor.moveToNext())
                assertEquals(
                    "content://video/two",
                    cursor.getString(cursor.getColumnIndexOrThrow("uri")),
                )
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("audio_track_index")))
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("last_played_time")))
                assertEquals(
                    "",
                    cursor.getString(cursor.getColumnIndexOrThrow("external_audio")),
                )
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
