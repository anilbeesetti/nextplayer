package dev.anilbeesetti.nextplayer.feature.player.extensions

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import dev.anilbeesetti.nextplayer.core.domain.model.PlayerItem
import java.io.File

/**
 * Converts [File] to [MediaItem.SubtitleConfiguration]
 */
fun File.toSubtitleConfiguration(): MediaItem.SubtitleConfiguration {
    return MediaItem.SubtitleConfiguration
        .Builder(this.toUri())
        .setMimeType(MimeTypes.APPLICATION_SUBRIP)
        .setLabel(this.nameWithoutExtension)
        .build()
}

/**
 * Converts [PlayerItem] to [MediaItem]
 */
fun PlayerItem.toMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setUri(Uri.parse(this.uriString))
        .setMediaId(this.path)
        .setSubtitleConfigurations(this.subtitleTracks.map { it.toSubtitleConfiguration() })
        .build()
}
