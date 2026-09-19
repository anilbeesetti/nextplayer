package dev.anilbeesetti.nextplayer.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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
        helper.createDatabase(TEST_DB, 8).apply {
            execSQL(
                "INSERT INTO network_connection " +
                    "(id,name,protocol,host,port,path,username,password,use_https,created_at) " +
                    "VALUES (1,'NAS','FTP','10.0.2.2',2121,'/media','alice','secret',0,123)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            9,
            true,
            MediaDatabase.MIGRATION_8_9,
        ).use { db ->
            db.query("SELECT * FROM network_connection WHERE id = 1").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(cursor.getColumnIndexOrThrow("id")))
                assertEquals("NAS", cursor.getString(cursor.getColumnIndexOrThrow("name")))
                assertEquals("FTP", cursor.getString(cursor.getColumnIndexOrThrow("protocol")))
                assertEquals("10.0.2.2", cursor.getString(cursor.getColumnIndexOrThrow("host")))
                assertEquals(2121, cursor.getInt(cursor.getColumnIndexOrThrow("port")))
                assertEquals("/media", cursor.getString(cursor.getColumnIndexOrThrow("path")))
                assertEquals("alice", cursor.getString(cursor.getColumnIndexOrThrow("username")))
                assertEquals("secret", cursor.getString(cursor.getColumnIndexOrThrow("password")))
                assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("use_https")))
                assertEquals(123L, cursor.getLong(cursor.getColumnIndexOrThrow("created_at")))
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
        helper.createDatabase("audio-migration", 11).apply {
            execSQL(
                "INSERT INTO media_state " +
                    "(uri, playback_position, external_subs, video_scale, subtitle_delay, subtitle_speed) " +
                    "VALUES ('content://video/one', 12345, 'file:///captions.srt', 1, 0, 1)",
            )
            close()
        }
        helper.runMigrationsAndValidate("audio-migration", 12, true, MediaDatabase.MIGRATION_11_12).use { db ->
            db.query("SELECT playback_position, external_subs, external_audio FROM media_state").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(12345L, cursor.getLong(0))
                assertEquals("file:///captions.srt", cursor.getString(1))
                assertEquals("", cursor.getString(2))
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
