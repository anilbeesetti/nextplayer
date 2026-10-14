package dev.anilbeesetti.nextplayer.feature.player.utils

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.anilbeesetti.nextplayer.core.model.PlaylistRecord

object PlaylistPlaybackContract {
    const val EXTRA_PLAYLIST_ID =
        "dev.anilbeesetti.nextplayer.extra.PLAYLIST_ID"
}

private const val EXPLICIT_PLAYLIST_KEY = "dev.anilbeesetti.nextplayer.extra.EXPLICIT_PLAYLIST"

internal val MediaItem.isExplicitPlaylistItem: Boolean
    get() = requestMetadata.extras?.getBoolean(EXPLICIT_PLAYLIST_KEY) == true

internal fun MediaItem.asExplicitPlaylistItem(): MediaItem = buildUpon()
    .setRequestMetadata(
        requestMetadata.buildUpon().setExtras(
            (requestMetadata.extras?.let(::Bundle) ?: Bundle()).apply {
                putBoolean(EXPLICIT_PLAYLIST_KEY, true)
            },
        ).build(),
    ).build()

internal data class PlaylistMediaQueue(
    val mediaItems: List<MediaItem>,
    val startIndex: Int,
)

internal fun PlaylistRecord.toMediaQueue(
    selectedUri: String,
): PlaylistMediaQueue? {
    val startIndex = items.indexOfFirst { it.uri == selectedUri }
        .takeIf { it >= 0 }
        ?: return null

    return PlaylistMediaQueue(
        mediaItems = items.map { item ->
            MediaItem.Builder()
                .setUri(item.uri)
                .setMediaId(item.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtworkUri(
                            item.tvgLogo
                                ?.takeIf(String::isNotBlank)
                                ?.toUri(),
                        )
                        .build(),
                )
                .build()
                .asExplicitPlaylistItem()
        },
        startIndex = startIndex,
    )
}
