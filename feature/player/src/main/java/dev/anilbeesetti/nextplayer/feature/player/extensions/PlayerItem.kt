package dev.anilbeesetti.nextplayer.feature.player.extensions

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import dev.anilbeesetti.nextplayer.core.domain.model.PlayerItem

/**
 * Converts [PlayerItem] to [MediaItem]
 */
fun PlayerItem.toMediaItem(context: Context, type: String?): MediaItem {
    val mediaItemBuilder = MediaItem.Builder()
        .setUri(Uri.parse(this.uriString))
        .setMediaId(this.path)
        .setSubtitleConfigurations(
            this.subtitleTracks.map { it.toUri().toSubtitleConfiguration(context, false) }
        )

    type?.let { mediaItemBuilder.setMimeType(it) }
    return mediaItemBuilder.build()
}
