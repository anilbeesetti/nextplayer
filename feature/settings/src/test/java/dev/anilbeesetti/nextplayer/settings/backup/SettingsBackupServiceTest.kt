package dev.anilbeesetti.nextplayer.settings.backup

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import dev.anilbeesetti.nextplayer.core.data.repository.NetworkConnectionRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakePreferencesRepository
import dev.anilbeesetti.nextplayer.core.media.network.keys.SshKeyStore
import dev.anilbeesetti.nextplayer.core.media.network.keys.StagedSshKey
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SettingsBackupServiceTest {
    private val preferences = FakePreferencesRepository()
    private val service = SettingsBackupService(
        context = RuntimeEnvironment.getApplication(),
        preferencesRepository = preferences,
        networkConnectionRepository = object : NetworkConnectionRepository {
            override fun getConnections(): Flow<List<NetworkConnection>> = flowOf(emptyList())
            override suspend fun getConnection(id: Long): NetworkConnection? = null
            override suspend fun upsert(connection: NetworkConnection): Long = error("Not used")
            override suspend fun delete(id: Long) = Unit
        },
        sshKeyStore = object : SshKeyStore {
            override suspend fun stage(uri: Uri): StagedSshKey = error("Not used")
            override fun resolve(fileName: String): File = error("Not used")
            override suspend fun commit(fileName: String): String = error("Not used")
            override suspend fun delete(fileName: String) = Unit
        },
    )

    @Test
    @Suppress("DEPRECATION")
    fun legacyAndroidExportsReadableJsonToDownloads() = runBlocking {
        preferences.updatePlayerPreferences { it.copy(autoplay = false) }
        val uri = service.exportSettings(includeNetworkConnections = false)
        val file = File(requireNotNull(uri.path))
        try {
            assertEquals(
                "file",
                uri.scheme,
            )
            assertEquals(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                file.parentFile,
            )
            assertTrue(file.name.startsWith("nextplayer-settings-"))
            val content = service.readSettings(uri)
            preferences.updatePlayerPreferences { it.copy(autoplay = true) }
            service.importSettings(content)
            assertFalse(preferences.playerPreferences.value.autoplay)
        } finally {
            file.delete()
        }
    }

    @Test
    fun malformedDocumentDoesNotChangeAnyPreferences() = runBlocking {
        val application = preferences.applicationPreferences.value
        val player = preferences.playerPreferences.value
        try {
            service.importSettings("{broken")
            error("Malformed settings were accepted")
        } catch (_: IllegalArgumentException) {
            assertEquals(
                application,
                preferences.applicationPreferences.value,
            )
            assertEquals(
                player,
                preferences.playerPreferences.value,
            )
        }
    }

    @Test
    @Config(sdk = [35])
    fun failedDownloadWriteRemovesThePendingDocument() = runBlocking {
        val provider = FailedWriteProvider()
        provider.attachInfo(
            RuntimeEnvironment.getApplication(),
            ProviderInfo().apply { authority = "media" },
        )
        ShadowContentResolver.registerProviderInternal(
            "media",
            provider,
        )
        try {
            service.exportSettings(includeNetworkConnections = false)
            error("A failed write was reported as an export")
        } catch (_: FileNotFoundException) {
            assertTrue(provider.inserted)
            assertEquals(
                listOf(provider.document),
                provider.deleted,
            )
        }
    }

    private class FailedWriteProvider : ContentProvider() {
        val document: Uri = Uri.parse("content://media/external/downloads/42")
        val deleted = mutableListOf<Uri>()
        var inserted = false

        override fun onCreate(): Boolean = true
        override fun getType(uri: Uri): String = "application/json"
        override fun insert(uri: Uri, values: ContentValues?): Uri {
            inserted = true
            return document
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
            throw FileNotFoundException("Cannot write document")

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            deleted.add(uri)
            return 1
        }

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 1

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null
    }
}
