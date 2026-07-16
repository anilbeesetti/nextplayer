package dev.anilbeesetti.nextplayer.core.media.network.keys

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@Singleton
class DefaultSshKeyStore @Inject constructor(
    @ApplicationContext context: Context,
) : SshKeyStore {

    private val contentResolver = context.contentResolver
    private val keyFiles = SshKeyFiles(
        stagingDirectory = File(context.noBackupFilesDir, "$KEY_DIRECTORY/staging"),
        committedDirectory = File(context.noBackupFilesDir, "$KEY_DIRECTORY/committed"),
    )

    override suspend fun stage(uri: Uri): StagedSshKey = stageSshKey(
        keyFiles = keyFiles,
        ioDispatcher = Dispatchers.IO,
        displayName = { queryDisplayName(uri) },
        inputStream = { contentResolver.openInputStream(uri) },
    )

    override fun resolve(fileName: String): File = keyFiles.resolve(fileName)

    override suspend fun commit(fileName: String): String = withContext(Dispatchers.IO) {
        keyFiles.commit(fileName)
    }

    override suspend fun delete(fileName: String): Unit = withContext(Dispatchers.IO) {
        keyFiles.delete(fileName)
    }

    private fun queryDisplayName(uri: Uri): String {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        return contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val displayNameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (displayNameColumn >= 0 && cursor.moveToFirst()) cursor.getString(displayNameColumn) else null
        }.orEmpty().ifBlank {
            uri.lastPathSegment.orEmpty().ifBlank { DEFAULT_DISPLAY_NAME }
        }
    }

    private companion object {
        const val KEY_DIRECTORY = "ssh_keys"
        const val DEFAULT_DISPLAY_NAME = "Private key"
    }
}

internal suspend fun stageSshKey(
    keyFiles: SshKeyFiles,
    ioDispatcher: CoroutineDispatcher,
    displayName: () -> String,
    inputStream: () -> InputStream?,
): StagedSshKey {
    var stagedFileName: String? = null
    try {
        return withContext(ioDispatcher) {
            val resolvedDisplayName = displayName()
            val fileName = inputStream()?.let(keyFiles::stage)
                ?: throw FileNotFoundException("Private key is missing")
            stagedFileName = fileName
            StagedSshKey(fileName = fileName, displayName = resolvedDisplayName)
        }
    } catch (throwable: Throwable) {
        stagedFileName?.let { fileName ->
            try {
                withContext(NonCancellable + ioDispatcher) {
                    keyFiles.delete(fileName)
                }
            } catch (cleanupFailure: Throwable) {
                throwable.addSuppressed(cleanupFailure)
            }
        }
        throw throwable
    }
}
