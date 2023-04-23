package dev.anilbeesetti.nextplayer.feature.player.extensions

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import dev.anilbeesetti.nextplayer.core.common.extensions.getFilenameFromUri
import dev.anilbeesetti.nextplayer.core.domain.model.PlayerItem
import java.io.File

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
 * Converts [PlayerItem] to [MediaItem]
 */
fun PlayerItem.toMediaItem(context: Context): MediaItem {
    return MediaItem.Builder()
        .setUri(Uri.parse(this.uriString))
        .setMediaId(this.path)
        .setSubtitleConfigurations(
            this.subtitleTracks.map { it.toUri().toSubtitleConfiguration(context, false) }
        ).build()
}


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
