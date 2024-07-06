package dev.anilbeesetti.nextplayer.feature.player.extensions

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.core.net.toUri
import androidx.media3.common.MimeTypes
import dev.anilbeesetti.nextplayer.core.common.extensions.getFilenameFromUri
import dev.anilbeesetti.nextplayer.core.common.extensions.getPath
import dev.anilbeesetti.nextplayer.core.common.extensions.getSubtitles
import dev.anilbeesetti.nextplayer.feature.player.model.Subtitle
import java.io.File

fun Uri.getSubtitleMime(): String {
    return when {
        path?.endsWith(".ssa") == true || path?.endsWith(".ass") == true -> {
            MimeTypes.TEXT_SSA
        }

        path?.endsWith(".vtt") == true -> {
            MimeTypes.TEXT_VTT
        }

        path?.endsWith(".ttml") == true || path?.endsWith(".xml") == true || path?.endsWith(".dfxp") == true -> {
            MimeTypes.APPLICATION_TTML
        }

        else -> {
            MimeTypes.APPLICATION_SUBRIP
        }
    }
}

val Uri.isSchemaContent: Boolean
    get() = ContentResolver.SCHEME_CONTENT.equals(scheme, ignoreCase = true)

fun Uri.getLocalSubtitles(context: Context, excludeSubsList: List<Uri> = emptyList()): List<Subtitle> {
    return context.getPath(this)?.let { path ->
        val excludeSubsPathList = excludeSubsList.mapNotNull { context.getPath(it) }
        File(path).getSubtitles().mapNotNull { file ->
            if (file.path !in excludeSubsPathList) {
                Subtitle(
                    name = file.name,
                    uri = file.toUri(),
                    isSelected = false,
                )
            } else {
                null
            }
        }
    } ?: emptyList()
}

fun Uri.toSubtitle(context: Context) = Subtitle(
    name = context.getFilenameFromUri(this),
    uri = this,
    isSelected = false,
)

@Suppress("DEPRECATION")
fun Bundle.getParcelableUriArray(key: String): Array<out Parcelable>? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArray(key, Uri::class.java)
    } else {
        getParcelableArray(key)
    }
}
