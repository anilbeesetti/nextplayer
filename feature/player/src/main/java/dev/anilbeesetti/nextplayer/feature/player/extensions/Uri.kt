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
fun Uri.toMediaItem(context: Context, type: String?, extras: Bundle? = null): MediaItem {
    val subtitleConfigurations = mutableListOf<MediaItem.SubtitleConfiguration>()

    if (extras != null) {
        val subsEnable = extras.getParcelableUriArray(PlayerActivity.API_SUBS_ENABLE)

        val defaultSub = if (!subsEnable.isNullOrEmpty()) subsEnable[0] as Uri else null

        if (extras.containsKey(PlayerActivity.API_SUBS)) {
            val subs = extras.getParcelableUriArray(PlayerActivity.API_SUBS)
            val subsName = extras.getStringArray(PlayerActivity.API_SUBS_NAME)

            if (!subs.isNullOrEmpty()) {
                subtitleConfigurations += subs.mapIndexed { index, parcelable ->
                    val subtitle = parcelable as Uri
                    val subtitleName =
                        if (subsName != null && subsName.size > index) subsName[index] else null
                    subtitle.toSubtitleConfiguration(
                        context = context,
                        selected = subtitle == defaultSub,
                        name = subtitleName
                    )
                }
            }
        }
    } else {
        val path = context.getPath(this)
        path?.let {
            subtitleConfigurations += File(path).getSubtitles()
                .map { it.toUri().toSubtitleConfiguration(context, false) }
        }
    }
    val mediaItemBuilder = MediaItem.Builder()
        .setUri(this)
        .setSubtitleConfigurations(subtitleConfigurations)

    type?.let { mediaItemBuilder.setMimeType(type) }

    return mediaItemBuilder.build()
}

@Suppress("DEPRECATION")
fun Bundle.getParcelableUriArray(key: String): Array<out Parcelable>? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArray(key, Uri::class.java)
    } else {
        getParcelableArray(key)
    }
}
