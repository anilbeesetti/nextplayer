package dev.anilbeesetti.nextplayer.feature.player.extensions

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import dev.anilbeesetti.nextplayer.core.common.extensions.convertToUTF8
import dev.anilbeesetti.nextplayer.core.common.extensions.getFilenameFromUri
import java.nio.charset.Charset

fun Uri.getSubtitleMime(): String = when {
    path?.endsWith(suffix = ".ssa") == true || path?.endsWith(suffix = ".ass") == true -> {
        MimeTypes.TEXT_SSA
    }

    path?.endsWith(suffix = ".vtt") == true -> {
        MimeTypes.TEXT_VTT
    }

    path?.endsWith(suffix = ".ttml") == true || path?.endsWith(suffix = ".xml") == true ||
        path?.endsWith(suffix = ".dfxp") == true -> {
        MimeTypes.APPLICATION_TTML
    }

    else -> {
        MimeTypes.APPLICATION_SUBRIP
    }
}

val Uri.isSchemaContent: Boolean
    get() = ContentResolver.SCHEME_CONTENT.equals(
        other = scheme,
        ignoreCase = true,
    )

suspend fun Context.uriToSubtitleConfiguration(
    uri: Uri,
    subtitleEncoding: String = "",
    isSelected: Boolean = false,
    label: String? = null,
    filename: String? = null,
    useMediaSourceForNetwork: Boolean = false,
): MediaItem.SubtitleConfiguration {
    val charset = if (subtitleEncoding.isNotEmpty() && Charset.isSupported(subtitleEncoding)) {
        Charset.forName(subtitleEncoding)
    } else {
        null
    }
    val subtitleLabel = label ?: filename ?: getFilenameFromUri(uri)
    val mimeType = (filename?.let(Uri::parse) ?: uri).getSubtitleMime()
    val utf8ConvertedUri = if (useMediaSourceForNetwork && uri.scheme in listOf(
            "http",
            "https",
        )
    ) {
        uri
    } else {
        convertToUTF8(
            uri = uri,
            charset = charset,
        )
    }
    return MediaItem.SubtitleConfiguration.Builder(utf8ConvertedUri).apply {
        setId(uri.toString())
        setMimeType(mimeType)
        setLabel(subtitleLabel)
        if (isSelected) setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
    }.build()
}

/** Accept the documented Uri[] and legacy clients sending an ArrayList<Uri>. */
@Suppress("DEPRECATION")
fun Bundle.getParcelableUriArray(key: String): List<Uri?>? = when (val value = get(key)) {
    is Array<*> -> value.map { it as? Uri }
    is ArrayList<*> -> value.map { it as? Uri }
    else -> null
}
