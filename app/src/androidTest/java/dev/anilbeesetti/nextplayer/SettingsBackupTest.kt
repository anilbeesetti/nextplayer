package dev.anilbeesetti.nextplayer

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import dev.anilbeesetti.nextplayer.core.common.storagePermission
import dev.anilbeesetti.nextplayer.core.data.repository.NetworkConnectionRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.media.network.keys.SshKeyStore
import dev.anilbeesetti.nextplayer.core.model.NetworkAuthentication
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkProtocol
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.settings.backup.SettingsBackupService
import java.io.File
import java.security.KeyPairGenerator
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class SettingsBackupTest {
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(storagePermission)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val preferences by lazy { GlobalContext.get().get<PreferencesRepository>() }
    private val connections by lazy { GlobalContext.get().get<NetworkConnectionRepository>() }
    private val keys by lazy { GlobalContext.get().get<SshKeyStore>() }
    private val backup by lazy { GlobalContext.get().get<SettingsBackupService>() }
    private val prefix = "backup-test-${UUID.randomUUID()}"
    private val originalKeys = mutableSetOf<String>()
    private val createdDocuments = mutableSetOf<Uri>()
    private var originalBackups = emptySet<Uri>()
    private val originalApplication by lazy { preferences.applicationPreferences.value }
    private val originalPlayer by lazy { preferences.playerPreferences.value }

    @Before
    fun openGeneralSettings() {
        originalApplication
        originalPlayer
        originalBackups = downloads()
        runBlocking {
            preferences.updatePlayerPreferences { it.copy(autoplay = false) }
            withTimeout(5_000) { preferences.playerPreferences.first { !it.autoplay } }
        }
        composeRule.onNodeWithContentDescription(
            label = composeRule.activity.getString(R.string.settings),
        ).performClick()
        composeRule.onNodeWithText(
            text = composeRule.activity.getString(R.string.general_name),
        ).performScrollTo().performClick()
    }

    @Test
    fun exportDialogWarnsAndDefaultsCredentialsOff() {
        click(R.string.settings_export)

        composeRule.onNodeWithText(
            text = text(R.string.settings_export_include_credentials),
        ).assertIsOff().performClick().assertIsOn()
        composeRule.onNodeWithText(
            text = text(R.string.settings_export_credentials_warning),
        ).assertIsDisplayed()
        click(R.string.cancel)
        click(R.string.settings_export)
        composeRule.onNodeWithText(
            text = text(R.string.settings_export_include_credentials),
        ).assertIsOff()
    }

    @Test
    fun exportCreatesJsonInDownloadsWithoutCredentials() {
        click(R.string.settings_export)
        click(R.string.export_action)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            (downloads() - originalBackups).isNotEmpty()
        }
        val uri = (downloads() - originalBackups).single()
        val root = JSONObject(runBlocking { backup.readSettings(uri) })

        assertFalse(root.getJSONObject("playerPreferences").getBoolean("autoplay"))
        assertTrue(root.getJSONObject("playerPreferences").has("subtitleTextSize"))
        assertFalse(root.has("networkConnections"))
        assertFalse(root.has("sshKeys"))
        composeRule.activity.contentResolver.query(
            uri,
            arrayOf(MediaStore.Downloads.RELATIVE_PATH),
            null,
            null,
            null,
        )!!.use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(
                "${Environment.DIRECTORY_DOWNLOADS}/",
                cursor.getString(0),
            )
        }
    }

    @Test
    fun importOnlyOverwritesAfterConfirmationAndPersistsValidSettings() {
        val content = """{
            "version":999,
            "playerPreferences":{
                "autoplay":true,"defaultPlaybackSpeed":2,"futureField":true,
                "seekIncrement":"wrong","loopMode":"FUTURE"
            }
        }"""
        chooseImport(content)
        composeRule.onNodeWithText(
            text = text(R.string.settings_import_confirmation),
        ).assertIsDisplayed()
        assertFalse(preferences.playerPreferences.value.autoplay)
        click(R.string.cancel)
        assertFalse(preferences.playerPreferences.value.autoplay)

        chooseImport(content)
        click(R.string.import_action)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            preferences.playerPreferences.value.let { it.autoplay && it.defaultPlaybackSpeed == 2f }
        }
        composeRule.onNodeWithText(
            text = text(R.string.settings_import_confirmation),
        ).assertDoesNotExist()
        val persisted = File(
            composeRule.activity.filesDir,
            "datastore/player_preferences.json",
        ).readText()
        assertEquals(
            2.0,
            JSONObject(persisted).getDouble("defaultPlaybackSpeed"),
            0.001,
        )
        assertEquals(
            originalPlayer.seekIncrement,
            preferences.playerPreferences.value.seekIncrement,
        )
    }

    @Test
    fun optionalConnectionsAndPrivateKeysSurviveExportAndImportWithFreshLocalIds() = runBlocking {
        val source = File.createTempFile(
            "settings-test-",
            ".key",
            composeRule.activity.cacheDir,
        )
        val keyBytes = privateKey()
        source.writeBytes(keyBytes)
        val key = keys.stage(Uri.fromFile(source))
        val keyName = keys.commit(key.fileName)
        originalKeys.add(keyName)
        source.delete()
        val name = "$prefix-sftp"
        val originalId = connections.upsert(
            NetworkConnection(
                name = name,
                host = "backup.invalid",
                protocol = NetworkProtocol.SFTP,
                username = "alice",
                password = "password-fixture",
                authentication = NetworkAuthentication.SSH_KEY,
                privateKeyFileName = keyName,
                privateKeyPassphrase = "key-passphrase",
            ),
        )
        val uri = backup.exportSettings(includeNetworkConnections = true)
        val content = backup.readSettings(uri)
        val root = JSONObject(content)
        assertEquals(
            keyBytes.toList(),
            Base64.decode(
                root.getJSONObject("sshKeys").getString(keyName),
                Base64.DEFAULT,
            ).toList(),
        )
        connections.delete(originalId)
        backup.importSettings(content)
        val restored = connections.getConnections().first().single { it.name == name }

        assertNotEquals(
            originalId,
            restored.id,
        )
        assertNotEquals(
            keyName,
            restored.privateKeyFileName,
        )
        assertEquals(
            "password-fixture",
            restored.password,
        )
        assertEquals(
            "key-passphrase",
            restored.privateKeyPassphrase,
        )
        assertEquals(
            keyBytes.toList(),
            keys.resolve(restored.privateKeyFileName).readBytes().toList(),
        )
    }

    @Test
    fun invalidKeysAndConnectionsDoNotDiscardValidPreferencesOrExistingCredentials() = runBlocking {
        val name = "$prefix-invalid"
        connections.upsert(
            NetworkConnection(
                name = name,
                host = "backup.invalid",
                protocol = NetworkProtocol.SMB,
                password = "keep",
            ),
        )
        backup.importSettings(
            """{"playerPreferences":{"rememberSelections":false},"networkConnections":[{"name":"$name","host":"backup.invalid","protocol":"SMB","password":123},{"name":"$prefix-new","host":"backup.invalid","protocol":"SFTP","authentication":"SSH_KEY","privateKeyFileName":"../../outside"}],"sshKeys":{"../../outside":"%%%"}}""",
        )

        assertFalse(preferences.playerPreferences.value.rememberSelections)
        assertEquals(
            "keep",
            connections.getConnections().first().single {
                it.name == name
            }.password,
        )
        assertFalse(connections.getConnections().first().any { it.name == "$prefix-new" })
    }

    @Test
    fun missingKeyPreservesTheExistingKeyAndItsPassphrase() = runBlocking {
        val existing = sshConnection("missing")
        backup.importSettings(
            sshBackup(
                existing,
                "missing",
                "wrong-passphrase",
                null,
            ).toString(),
        )
        val restored = connections.getConnection(existing.id)!!

        assertEquals(
            existing.privateKeyFileName,
            restored.privateKeyFileName,
        )
        assertEquals(
            existing.privateKeyPassphrase,
            restored.privateKeyPassphrase,
        )
        assertTrue(keys.resolve(restored.privateKeyFileName).isFile)
    }

    @Test
    fun duplicateEntriesPreserveTheLatestValidKeyWithoutCreatingDuplicateConnections() =
        runBlocking {
            val existing = sshConnection("duplicate")
            val replacement = privateKey()
            val content = sshBackup(
                existing,
                "replacement",
                "replacement-passphrase",
                replacement,
            )
            content.getJSONArray("networkConnections").put(
                JSONObject().put(
                    "name",
                    existing.name,
                ).put(
                    "host",
                    existing.host,
                ).put(
                    "protocol",
                    "SFTP",
                ),
            )
            backup.importSettings(content.toString())
            val restored = connections.getConnections().first().single { it.name == existing.name }

            assertEquals(
                replacement.toList(),
                keys.resolve(restored.privateKeyFileName).readBytes().toList(),
            )
            assertEquals(
                "replacement-passphrase",
                restored.privateKeyPassphrase,
            )

            connections.delete(restored.id)
            keys.delete(restored.privateKeyFileName)
            backup.importSettings(content.toString())
            assertEquals(
                1,
                connections.getConnections().first().count { it.name == existing.name },
            )
        }

    @Test
    fun corruptKeyBytesPreserveExistingCredentialsAndSkipUnusableNewConnections() = runBlocking {
        val existing = sshConnection("corrupt")
        val content = sshBackup(
            existing,
            "bad",
            "wrong-passphrase",
            "not a key".toByteArray(),
        )
        content.getJSONArray("networkConnections").put(
            JSONObject().put(
                "name",
                "$prefix-new",
            ).put(
                "host",
                "backup.invalid",
            )
                .put(
                    "protocol",
                    "SFTP",
                ).put(
                    "authentication",
                    "SSH_KEY",
                )
                .put(
                    "privateKeyFileName",
                    "bad",
                ),
        )
        content.put(
            "playerPreferences",
            JSONObject().put(
                "rememberSelections",
                false,
            ),
        )
        backup.importSettings(content.toString())
        val restored = connections.getConnection(existing.id)!!

        assertEquals(
            existing.privateKeyFileName,
            restored.privateKeyFileName,
        )
        assertEquals(
            existing.privateKeyPassphrase,
            restored.privateKeyPassphrase,
        )
        assertTrue(keys.resolve(restored.privateKeyFileName).isFile)
        assertFalse(connections.getConnections().first().any { it.name == "$prefix-new" })
        assertFalse(preferences.playerPreferences.value.rememberSelections)
    }

    @After
    fun cleanup() = runBlocking {
        connections.getConnections().first().filter { it.name.startsWith(prefix) }.forEach {
            connections.delete(it.id)
            if (it.privateKeyFileName.isNotBlank()) keys.delete(it.privateKeyFileName)
        }
        originalKeys.forEach { keys.delete(it) }
        (downloads() - originalBackups + createdDocuments).forEach {
            composeRule.activity.contentResolver.delete(
                it,
                null,
                null,
            )
        }
        preferences.updateApplicationPreferences { originalApplication }
        preferences.updatePlayerPreferences { originalPlayer }
    }

    private fun chooseImport(content: String) {
        val resolver = composeRule.activity.contentResolver
        val values = ContentValues().apply {
            put(
                MediaStore.Downloads.DISPLAY_NAME,
                "nextplayer-settings-$prefix.json",
            )
            put(
                MediaStore.Downloads.MIME_TYPE,
                "application/json",
            )
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS,
            )
        }
        val uri = checkNotNull(
            resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values,
            ),
        )
        createdDocuments.add(uri)
        resolver.openOutputStream(uri)!!.use { it.write(content.toByteArray()) }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? =
                if (intent.action == Intent.ACTION_OPEN_DOCUMENT) {
                    Instrumentation.ActivityResult(
                        Activity.RESULT_OK,
                        Intent().setData(uri),
                    )
                } else {
                    null
                }
        }
        instrumentation.addMonitor(monitor)
        try {
            click(R.string.settings_import)
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithText(text = text(R.string.settings_import_confirmation))
                    .fetchSemanticsNodes().isNotEmpty()
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun downloads(): Set<Uri> {
        val result = mutableSetOf<Uri>()
        composeRule.activity.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
            arrayOf("nextplayer-settings-%"),
            null,
        )!!.use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        cursor.getLong(0),
                    ),
                )
            }
        }
        return result
    }

    private fun text(id: Int) = composeRule.activity.getString(id)

    private fun privateKey(): ByteArray {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val encoded = Base64.encodeToString(
            pair.private.encoded,
            Base64.NO_WRAP,
        )
            .chunked(64).joinToString("\n")
        return "-----BEGIN PRIVATE KEY-----\n$encoded\n-----END PRIVATE KEY-----\n".toByteArray()
    }

    private suspend fun sshConnection(suffix: String): NetworkConnection {
        val file = File.createTempFile(
            "settings-test-",
            ".key",
            composeRule.activity.cacheDir,
        )
        val keyName = try {
            file.writeBytes(privateKey())
            keys.commit(keys.stage(Uri.fromFile(file)).fileName)
        } finally {
            file.delete()
        }
        originalKeys.add(keyName)
        val connection = NetworkConnection(
            name = "$prefix-$suffix",
            host = "backup.invalid",
            protocol = NetworkProtocol.SFTP,
            username = "alice",
            authentication = NetworkAuthentication.SSH_KEY,
            privateKeyFileName = keyName,
            privateKeyPassphrase = "original-passphrase",
        )
        return connection.copy(id = connections.upsert(connection))
    }

    private fun sshBackup(
        connection: NetworkConnection,
        keyName: String,
        passphrase: String,
        data: ByteArray?,
    ): JSONObject {
        val entry = JSONObject().put(
            "name",
            connection.name,
        ).put(
            "host",
            connection.host,
        )
            .put(
                "protocol",
                "SFTP",
            ).put(
                "authentication",
                "SSH_KEY",
            )
            .put(
                "privateKeyFileName",
                keyName,
            ).put(
                "privateKeyPassphrase",
                passphrase,
            )
        val content = JSONObject().put(
            "networkConnections",
            JSONArray().put(entry),
        )
        if (data != null) {
            content.put(
                "sshKeys",
                JSONObject().put(
                    keyName,
                    Base64.encodeToString(
                        data,
                        Base64.NO_WRAP,
                    ),
                ),
            )
        }
        return content
    }

    private fun click(id: Int) {
        composeRule.onNodeWithText(text = text(id)).performClick()
    }
}
