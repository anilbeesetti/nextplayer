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
                assertEquals("NAS", cursor.getString(cursor.getColumnIndexOrThrow("name")))
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
