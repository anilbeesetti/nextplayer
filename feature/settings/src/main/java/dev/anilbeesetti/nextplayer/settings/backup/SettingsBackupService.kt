package dev.anilbeesetti.nextplayer.settings.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import dev.anilbeesetti.nextplayer.core.data.repository.NetworkConnectionRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.media.network.keys.SshKeyStore
import dev.anilbeesetti.nextplayer.core.media.network.keys.isValidSshPrivateKey
import dev.anilbeesetti.nextplayer.core.model.NetworkAuthentication
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.ui.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single

@Single
class SettingsBackupService(
    private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val networkConnectionRepository: NetworkConnectionRepository,
    private val sshKeyStore: SshKeyStore,
) {
    private val codec = SettingsBackupCodec(
        subtitleEncodings = context.resources.getStringArray(R.array.charsets_list).map {
            it.substringAfterLast(
                delimiter = "(",
                missingDelimiterValue = "",
            )
                .substringBeforeLast(delimiter = ")")
        }.toSet(),
    )

    suspend fun exportSettings(includeNetworkConnections: Boolean): Uri =
        withContext(Dispatchers.IO) {
            val connections = if (includeNetworkConnections) {
                networkConnectionRepository.getConnections().first()
            } else {
                null
            }
            val keys = connections.orEmpty().filter {
                it.authentication == NetworkAuthentication.SSH_KEY &&
                    it.privateKeyFileName.isNotBlank()
            }.map { it.privateKeyFileName }.distinct().associateWith { fileName ->
                val file = sshKeyStore.resolve(fileName)
                require(file.length() in 1..MAX_KEY_BYTES.toLong())
                Base64.encodeToString(
                    file.readBytes(),
                    Base64.NO_WRAP,
                )
            }
            val bytes = codec.encode(
                applicationPreferences = preferencesRepository.applicationPreferences.value,
                playerPreferences = preferencesRepository.playerPreferences.value,
                networkConnections = connections,
                sshKeys = keys,
            ).toByteArray(Charsets.UTF_8)
            require(bytes.size <= MAX_BACKUP_BYTES)
            val timestamp = SimpleDateFormat(
                "yyyyMMdd-HHmmss-SSS",
                Locale.ROOT,
            ).format(Date())
            val fileName = "nextplayer-settings-$timestamp.json"

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return@withContext exportLegacy(
                    fileName,
                    bytes,
                )
            }
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(
                    MediaStore.Downloads.DISPLAY_NAME,
                    fileName,
                )
                put(
                    MediaStore.Downloads.MIME_TYPE,
                    "application/json",
                )
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS,
                )
                put(
                    MediaStore.Downloads.IS_PENDING,
                    1,
                )
            }
            val uri = checkNotNull(
                resolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values,
                ),
            )
            try {
                checkNotNull(resolver.openOutputStream(uri)).use { it.write(bytes) }
                values.clear()
                values.put(
                    MediaStore.Downloads.IS_PENDING,
                    0,
                )
                check(
                    resolver.update(
                        uri,
                        values,
                        null,
                        null,
                    ) == 1,
                )
                uri
            } catch (exception: Exception) {
                try {
                    resolver.delete(
                        uri,
                        null,
                        null,
                    )
                } catch (cleanupFailure: Exception) {
                    exception.addSuppressed(cleanupFailure)
                }
                throw exception
            }
        }

    suspend fun readSettings(uri: Uri): String = withContext(Dispatchers.IO) {
        val content = checkNotNull(context.contentResolver.openInputStream(uri)).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_BACKUP_BYTES)
                output.write(
                    buffer,
                    0,
                    count,
                )
            }
            output.toByteArray().decodeToString(throwOnInvalidSequence = true)
                .removePrefix("\uFEFF")
        }
        codec.parse(content)
        content
    }

    suspend fun importSettings(content: String): Unit = withContext(Dispatchers.IO) {
        val existingConnections = networkConnectionRepository.getConnections().first()
        val imported = codec.decode(
            content = codec.parse(content),
            applicationPreferences = preferencesRepository.applicationPreferences.value,
            playerPreferences = preferencesRepository.playerPreferences.value,
            networkConnections = existingConnections,
        )
        imported.networkConnections.orEmpty().forEach { connection ->
            importConnection(
                connection,
                imported.sshKeys,
                existingConnections,
            )
        }
        preferencesRepository.updateApplicationPreferences { imported.applicationPreferences }
        preferencesRepository.updatePlayerPreferences { imported.playerPreferences }
        // Preference data sources log storage failures; do not report an import that did not persist.
        check(
            withTimeoutOrNull(5_000) {
                preferencesRepository.applicationPreferences.first {
                    it ==
                        imported.applicationPreferences
                }
                preferencesRepository.playerPreferences.first { it == imported.playerPreferences }
                true
            } == true,
        )
    }

    private suspend fun importConnection(
        connection: NetworkConnection,
        keys: Map<String, String>,
        existingConnections: List<NetworkConnection>,
    ) {
        val existing = existingConnections.firstOrNull { it.id == connection.id }
        var newKey: String? = null
        var restored = connection
        if (connection.authentication == NetworkAuthentication.SSH_KEY) {
            val data = keys[connection.privateKeyFileName]?.takeIf {
                it.length <= (MAX_KEY_BYTES + 2) / 3 * 4
            }?.let { encoded ->
                try {
                    Base64.decode(
                        encoded,
                        Base64.DEFAULT,
                    ).takeIf { it.size in 1..MAX_KEY_BYTES }
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
            var file: File? = null
            try {
                if (data != null) {
                    file = File.createTempFile(
                        "settings-key-",
                        ".key",
                        context.cacheDir,
                    )
                    file.writeBytes(data)
                }
                val candidate = file
                if (candidate != null &&
                    isValidSshPrivateKey(
                        candidate,
                        connection.privateKeyPassphrase,
                    )
                ) {
                    newKey = sshKeyStore.stage(Uri.fromFile(candidate)).fileName
                    restored = connection.copy(privateKeyFileName = sshKeyStore.commit(newKey))
                } else {
                    val existingKey =
                        existing?.privateKeyFileName?.takeIf { it.isNotBlank() } ?: return
                    restored = connection.copy(
                        privateKeyFileName = existingKey,
                        privateKeyPassphrase = existing.privateKeyPassphrase,
                    )
                }
            } catch (exception: Exception) {
                newKey?.let { cleanupKey(it) }
                throw exception
            } finally {
                file?.delete()
            }
        } else {
            restored = connection.copy(
                privateKeyFileName = "",
                privateKeyPassphrase = "",
            )
        }
        try {
            networkConnectionRepository.upsert(restored)
        } catch (exception: Exception) {
            newKey?.let { cleanupKey(it) }
            throw exception
        }
        existing?.privateKeyFileName?.takeIf {
            it.isNotBlank() && it != restored.privateKeyFileName
        }?.let { cleanupKey(it) }
    }

    private suspend fun cleanupKey(fileName: String) =
        withContext(NonCancellable + Dispatchers.IO) {
            try {
                if (networkConnectionRepository.getConnections().first().none {
                        it.privateKeyFileName == fileName
                    }
                ) {
                    sshKeyStore.delete(fileName)
                }
            } catch (_: Exception) {
                // Startup reconciliation removes unreferenced keys if cleanup cannot finish here.
            }
        }

    @Suppress("DEPRECATION")
    private fun exportLegacy(fileName: String, bytes: ByteArray): Uri {
        val downloads = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS,
        )
        check(downloads.isDirectory || downloads.mkdirs())
        val file = File(
            downloads,
            fileName,
        )
        check(file.createNewFile())
        try {
            file.writeBytes(bytes)
            return Uri.fromFile(file)
        } catch (exception: Exception) {
            file.delete()
            throw exception
        }
    }

    private companion object {
        const val MAX_BACKUP_BYTES = 32 * 1024 * 1024
        const val MAX_KEY_BYTES = 1024 * 1024
    }
}
