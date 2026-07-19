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
    fun migrate7To8_preservesConnectionAndAddsPasswordDefaults() {
        helper.createDatabase(TEST_DB, 7).apply {
            execSQL(
                "INSERT INTO network_connection " +
                    "(id,name,protocol,host,port,path,username,password,use_https,created_at) " +
                    "VALUES (1,'NAS','FTP','10.0.2.2',2121,'/media','alice','secret',0,123)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            8,
            true,
            MediaDatabase.MIGRATION_7_8,
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

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
