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

