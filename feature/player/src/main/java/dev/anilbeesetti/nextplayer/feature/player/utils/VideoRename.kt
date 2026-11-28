package dev.anilbeesetti.nextplayer.feature.player.utils

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore

internal fun Uri.isMediaStoreVideo(): Boolean {
    if (scheme != ContentResolver.SCHEME_CONTENT || authority != MediaStore.AUTHORITY) return false
    val segments = pathSegments
    return segments.size == 4 && segments[1] == "video" &&
        segments[2] == "media" && segments[3].toLongOrNull() != null
}

internal data class VideoRename(val uri: Uri, val fileName: String) {
    val baseName: String = fileName.substringBeforeLast('.').ifBlank { fileName }
    private val extension = fileName.removePrefix(baseName)

    fun withBaseName(name: String): String {
        val hasExtension = name.endsWith(
            extension,
            ignoreCase = true,
        )
        return if (hasExtension) name else name + extension
    }
}
