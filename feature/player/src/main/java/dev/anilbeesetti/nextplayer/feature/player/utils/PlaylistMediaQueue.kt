package dev.anilbeesetti.nextplayer.feature.player.utils

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.anilbeesetti.nextplayer.core.model.Playlist

internal data class PlaybackRequestIdentity(
    val playlistId: Long?,
    val selectedUriString: String,
)

internal data class PlaylistMediaQueue(
    val mediaItems: List<MediaItem>,
    val startIndex: Int,
)

internal fun Playlist.toMediaQueue(selectedUri: String): PlaylistMediaQueue? {
    val startIndex = items.indexOfFirst { it.uriString == selectedUri }
    if (startIndex == -1) return null

    return PlaylistMediaQueue(
        mediaItems = items.map { item ->
            MediaItem.Builder()
                .setUri(item.uriString)
                .setMediaId(item.uriString)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtworkUri(item.imageUrl?.takeIf(String::isNotBlank)?.toUri())
                        .build(),
                )
                .build()
        },
        startIndex = startIndex,
    )
}
