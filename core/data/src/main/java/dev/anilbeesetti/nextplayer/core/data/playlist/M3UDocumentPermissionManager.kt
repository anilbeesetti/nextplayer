package dev.anilbeesetti.nextplayer.core.data.playlist

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class PersistedM3UGrant(
    val uri: Uri,
    val newlyPersisted: Boolean,
)

class M3UDocumentPermissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun acquire(uri: Uri): Result<PersistedM3UGrant> = runCatching {
        val alreadyPersisted = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
        if (!alreadyPersisted) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        PersistedM3UGrant(uri = uri, newlyPersisted = !alreadyPersisted)
    }

    fun release(grant: PersistedM3UGrant) {
        if (grant.newlyPersisted) release(grant.uri)
    }

    fun release(uri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // The document provider may already have revoked the grant.
        }
    }
}
