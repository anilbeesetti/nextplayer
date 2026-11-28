package dev.anilbeesetti.nextplayer.feature.player.utils

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.anilbeesetti.nextplayer.core.model.PlaylistRecord

object PlaylistPlaybackContract {
    const val EXTRA_PLAYLIST_ID =
        "dev.anilbeesetti.nextplayer.extra.PLAYLIST_ID"
    const val EXTRA_PLAYLIST_ITEM_ID =
        "dev.anilbeesetti.nextplayer.extra.PLAYLIST_ITEM_ID"
}

internal val MediaItem.playlistQueueKey: Any
    get() = requestMetadata.extras
        ?.getLong(PlaylistPlaybackContract.EXTRA_PLAYLIST_ITEM_ID)
        ?.takeIf { it > 0 }
        ?: mediaId

private const val EXPLICIT_PLAYLIST_KEY = "dev.anilbeesetti.nextplayer.extra.EXPLICIT_PLAYLIST"

internal val MediaItem.isExplicitPlaylistItem: Boolean
    get() = requestMetadata.extras?.getBoolean(EXPLICIT_PLAYLIST_KEY) == true

internal fun MediaItem.asExplicitPlaylistItem(): MediaItem = buildUpon()
    .setRequestMetadata(
        requestMetadata.buildUpon().setExtras(
            (requestMetadata.extras?.let(block = ::Bundle) ?: Bundle()).apply {
                putBoolean(
                    EXPLICIT_PLAYLIST_KEY,
                    true,
                )
            },
        ).build(),
    ).build()

internal data class PlaylistMediaQueue(val mediaItems: List<MediaItem>, val startIndex: Int)

internal fun PlaylistRecord.toMediaQueue(
    selectedUri: String,
    itemId: Long? = null,
): PlaylistMediaQueue? {
    val startIndex = items.indexOfFirst { item ->
        item.uri == selectedUri && (itemId == null || item.id == itemId)
    }.takeIf { it >= 0 } ?: return null

    return PlaylistMediaQueue(
        mediaItems = items.map { item ->
            MediaItem.Builder()
                .setUri(item.uri)
                .setMediaId(item.uri)
                .setRequestMetadata(
                    MediaItem.RequestMetadata.Builder()
                        .setExtras(
                            Bundle().apply {
                                putLong(
                                    PlaylistPlaybackContract.EXTRA_PLAYLIST_ITEM_ID,
                                    item.id,
                                )
                            },
                        )
                        .build(),
                )
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtworkUri(
                            item.tvgLogo
                                ?.takeIf(predicate = String::isNotBlank)
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
