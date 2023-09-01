package dev.anilbeesetti.nextplayer.feature.player.extensions

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun ContentResolver.grantUriPermissionIfNotGranted(uri: Uri) = withContext(Dispatchers.IO) {
    var permissionAlreadyTaken = false
    for (uriPermission in persistedUriPermissions) {
        if (uriPermission.uri.equals(uri)) {
            permissionAlreadyTaken = true
            break
        }
    }

    if (!permissionAlreadyTaken) {
        try {
            takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
