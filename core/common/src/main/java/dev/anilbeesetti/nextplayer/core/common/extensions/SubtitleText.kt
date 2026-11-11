package dev.anilbeesetti.nextplayer.core.common.extensions

import android.content.Context
import android.net.Uri
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mozilla.universalchardet.UniversalDetector

private const val CHARSET_DETECTION_MAX_BYTES = 100 * 1024

/**
 * Reads the subtitle file at [uri] and returns its text re-encoded as UTF-8, or null when the file
 * can't be opened. The source encoding is [charset] when given and detected from the content
 * otherwise.
 *
 * Unlike [convertToUTF8] nothing is written to the subtitle cache, so reading an additional
 * subtitle can never overwrite the cached copy of another one that has the same file name.
 */
suspend fun Context.readSubtitleAsUtf8(uri: Uri, charset: Charset? = null): ByteArray? {
    return withContext(Dispatchers.IO) {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext null
        val sourceCharset = charset ?: detectSubtitleCharset(bytes)
        if (sourceCharset == StandardCharsets.UTF_8) {
            bytes
        } else {
            bytes.toString(sourceCharset).toByteArray()
        }
    }
}

private fun detectSubtitleCharset(bytes: ByteArray): Charset {
    val detector = UniversalDetector(null)
    val sampleSize = bytes.size.coerceAtMost(CHARSET_DETECTION_MAX_BYTES)
    detector.handleData(
        bytes,
        0,
        sampleSize,
    )
    detector.dataEnd()
    val name = detector.detectedCharset
    return if (name != null && Charset.isSupported(name)) {
        Charset.forName(name)
    } else {
        StandardCharsets.UTF_8
    }
}
