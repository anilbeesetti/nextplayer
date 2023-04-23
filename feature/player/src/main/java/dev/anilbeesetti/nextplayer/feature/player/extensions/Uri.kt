package dev.anilbeesetti.nextplayer.feature.player.extensions

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import dev.anilbeesetti.nextplayer.core.common.extensions.getFilenameFromUri
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import timber.log.Timber

fun Uri.getSubtitleMime(): String {
    return if (path?.endsWith(".ssa") == true || path?.endsWith(".ass") == true) {
        MimeTypes.TEXT_SSA;
    } else if (path?.endsWith(".vtt") == true) {
        MimeTypes.TEXT_VTT;
    } else if (path?.endsWith(".ttml") == true || path?.endsWith(".xml") == true || path?.endsWith(".dfxp") == true) {
        MimeTypes.APPLICATION_TTML;
    } else {
        MimeTypes.APPLICATION_SUBRIP;
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
fun Uri.toMediaItem(context: Context, extras: Bundle? = null): MediaItem {
    val subtitleConfigurations = mutableListOf<MediaItem.SubtitleConfiguration>()
    if (extras != null) {

        val subsEnable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelableArray(PlayerActivity.API_SUBS_ENABLE, Uri::class.java)
        } else {
            extras.getParcelableArray(PlayerActivity.API_SUBS_ENABLE)
        }
        val defaultSub = if (!subsEnable.isNullOrEmpty()) subsEnable[0] as Uri else null

        if (extras.containsKey(PlayerActivity.API_SUBS)) {
            val subs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelableArray(PlayerActivity.API_SUBS, Uri::class.java)
            } else {
                extras.getParcelableArray(PlayerActivity.API_SUBS)
            }
            val subsName = extras.getStringArray(PlayerActivity.API_SUBS_NAME)
            if (!subs.isNullOrEmpty()) {
                for (i in subs.indices) {
                    val subtitle = subs[i] as Uri
                    val subName = if (subsName != null && subsName.size > i) subsName[i] else null
                    subtitleConfigurations += subtitle.toSubtitleConfiguration(
                        context = context,
                        selected = subtitle == defaultSub,
                        name = subName
                    )
                }
            }
        }
    }
    return MediaItem.Builder()
        .setUri(this)
        .setSubtitleConfigurations(subtitleConfigurations)
        .build()
}