package dev.anilbeesetti.nextplayer.feature.player.service

import android.os.Bundle
import androidx.media3.common.BundleListRetriever
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import kotlinx.coroutines.guava.await

/** Wait for the service to accept the queue before releasing the launch activity's controller. */
@UnstableApi
internal suspend fun MediaController.startBackgroundPlayback(
    items: List<MediaItem>,
    startIndex: Int,
    startPositionMs: Long,
    playbackSpeed: Float?,
): SessionResult = sendCustomCommand(
    CustomCommands.START_BACKGROUND_PLAYBACK.sessionCommand,
    Bundle().apply {
        putBinder(
            CustomCommands.BACKGROUND_MEDIA_ITEMS_KEY,
            BundleListRetriever(items.map { it.toBundleIncludeLocalConfiguration() }),
        )
        putInt(
            CustomCommands.BACKGROUND_START_INDEX_KEY,
            startIndex,
        )
        putLong(
            CustomCommands.BACKGROUND_START_POSITION_KEY,
            startPositionMs,
        )
        playbackSpeed?.let {
            putFloat(
                CustomCommands.PLAYBACK_SPEED_KEY,
                it,
            )
        }
    },
).await()
