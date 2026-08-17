package dev.anilbeesetti.nextplayer.feature.player.extensions

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import dev.anilbeesetti.nextplayer.core.common.extensions.convertToUTF8
import dev.anilbeesetti.nextplayer.core.common.extensions.getFilenameFromUri
import java.nio.charset.Charset

fun Uri.getSubtitleMime(context: Context): String {
    // Try to resolve a human-readable filename from the Uri.
    // For content:// URIs (from Android file picker), `path` often does NOT contain
    // the real filename or extension, so we rely on ContentResolver instead.
    val name = context.getFilenameFromUri(this)?.lowercase() ?: return MimeTypes.TEXT_VTT
    // If filename cannot be resolved, fallback to VTT.
    // VTT is more forgiving than SRT and avoids parser failures.


    return when {
        // SSA / ASS subtitles (styled subtitles)
        name.endsWith(".ssa") || name.endsWith(".ass") -> MimeTypes.TEXT_SSA

        // WebVTT subtitles (common for streaming, more tolerant format)
        name.endsWith(".vtt") -> MimeTypes.TEXT_VTT

        // TTML / XML-based subtitles (used in some streaming platforms)
        name.endsWith(".ttml") || name.endsWith(".xml") || name.endsWith(".dfxp") ->
            MimeTypes.APPLICATION_TTML

        // SubRip (.srt) subtitles (strict format, requires proper indexing and timestamps)
        name.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP

        // Fallback: default to VTT instead of SRT.
        // Reason:
        // - SRT parser is strict and may reject slightly malformed files
        // - VTT parser is more tolerant and works better as a safe default
        else -> MimeTypes.TEXT_VTT
    }
}

val Uri.isSchemaContent: Boolean
    get() = ContentResolver.SCHEME_CONTENT.equals(scheme, ignoreCase = true)

suspend fun Context.uriToSubtitleConfiguration(
    uri: Uri,
    subtitleEncoding: String = "",
    isSelected: Boolean = false,
): MediaItem.SubtitleConfiguration {
    val charset = if (subtitleEncoding.isNotEmpty() && Charset.isSupported(subtitleEncoding)) {
        Charset.forName(subtitleEncoding)
    } else {
        null
    }
    val label = getFilenameFromUri(uri)

    // Pass context to resolve the actual filename via ContentResolver.
    // `Uri.path` is unreliable for content:// URIs (e.g. from file picker)
    // and may not contain the file extension needed for MIME detection.
    val mimeType = uri.getSubtitleMime(this)
    val utf8ConvertedUri = convertToUTF8(uri = uri, charset = charset)
    return MediaItem.SubtitleConfiguration.Builder(utf8ConvertedUri).apply {
        setId(uri.toString())
        setMimeType(mimeType)
        setLabel(label)
        if (isSelected) setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
    }.build()
}

@Suppress("DEPRECATION")
fun Bundle.getParcelableUriArray(key: String): ArrayList<out Parcelable>? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayList(key, Uri::class.java)
    } else {
        getParcelableArrayList(key)
    }
}
