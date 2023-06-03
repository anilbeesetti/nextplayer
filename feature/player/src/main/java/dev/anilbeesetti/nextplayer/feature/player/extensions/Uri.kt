package dev.anilbeesetti.nextplayer.feature.player.extensions

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import dev.anilbeesetti.nextplayer.core.common.extensions.getFilenameFromUri
import dev.anilbeesetti.nextplayer.core.common.extensions.getPath
import dev.anilbeesetti.nextplayer.core.common.extensions.getSubtitles
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
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

/**
 * Converts [Uri] to [MediaItem.SubtitleConfiguration]
 */
fun Uri.toSubtitleConfiguration(
    context: Context,
    selected: Boolean,
    name: String? = null
): MediaItem.SubtitleConfiguration {
    val subtitleConfigurationBuilder = MediaItem.SubtitleConfiguration
        .Builder(this)
        .setMimeType(getSubtitleMime())
        .setLabel(name ?: context.getFilenameFromUri(this))
    if (selected) {
        subtitleConfigurationBuilder.setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
    }
    return subtitleConfigurationBuilder.build()
}

/**
 * Converts [Uri] to [MediaItem]
 */
fun Uri.toMediaItem(subtitles: List<Subtitle>, type: String?): MediaItem {
    val subtitleConfigurations = subtitles.map(Subtitle::toSubtitleConfiguration)
    val mediaItemBuilder = MediaItem.Builder()
        .setUri(this)
        .setSubtitleConfigurations(subtitleConfigurations)

    type?.let { mediaItemBuilder.setMimeType(type) }

    return mediaItemBuilder.build()
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

data class Subtitle(
    val name: String?,
    val uri: Uri,
    val isSelected: Boolean
)

fun Subtitle.toSubtitleConfiguration(): MediaItem.SubtitleConfiguration {
    val subtitleConfigurationBuilder = MediaItem.SubtitleConfiguration
        .Builder(uri)
        .setMimeType(uri.getSubtitleMime())
        .setLabel(name)
    if (isSelected) {
        subtitleConfigurationBuilder.setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
    }
    return subtitleConfigurationBuilder.build()
}

@Suppress("DEPRECATION")
fun Bundle.getParcelableUriArray(key: String): Array<out Parcelable>? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArray(key, Uri::class.java)
    } else {
        getParcelableArray(key)
    }
}
