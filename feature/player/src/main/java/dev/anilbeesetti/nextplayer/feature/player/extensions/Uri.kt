package dev.anilbeesetti.nextplayer.feature.player.extensions

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
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
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

fun Uri.getSubs(
    context: Context,
    extras: Bundle?,
    externalSubtitles: List<Uri> = emptyList()
): List<Subtitle> {
    val subtitles = mutableListOf<Subtitle>()

    if (extras != null && extras.containsKey(PlayerActivity.API_SUBS)) {
        val subsEnable = extras.getParcelableUriArray(PlayerActivity.API_SUBS_ENABLE)

        val defaultSub = if (!subsEnable.isNullOrEmpty()) subsEnable[0] as Uri else null

        val subs = extras.getParcelableUriArray(PlayerActivity.API_SUBS)
        val subsName = extras.getStringArray(PlayerActivity.API_SUBS_NAME)

        if (!subs.isNullOrEmpty()) {
            subtitles += subs.mapIndexed { index, parcelable ->
                val subtitleUri = parcelable as Uri
                val subtitleName =
                    if (subsName != null && subsName.size > index) subsName[index] else null
                Subtitle(
                    name = subtitleName,
                    uri = subtitleUri,
                    isSelected = subtitleUri == defaultSub && externalSubtitles.isEmpty()
                )
            }
        }
    }

    context.getPath(this)?.let { path ->
        subtitles += File(path).getSubtitles().mapIndexed { index, file ->
            Subtitle(
                name = file.name,
                uri = file.toUri(),
                isSelected = index == 0 && externalSubtitles.isEmpty()
            )
        }
    }

    subtitles += externalSubtitles.mapIndexed { index, uri ->
        Subtitle(
            name = context.getFilenameFromUri(uri),
            uri = uri,
            isSelected = index == (externalSubtitles.size - 1)
        )
    }

    return subtitles
}

@Suppress("DEPRECATION")
fun Bundle.getParcelableUriArray(key: String): Array<out Parcelable>? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArray(key, Uri::class.java)
    } else {
        getParcelableArray(key)
    }
}
