package dev.anilbeesetti.nextplayer.feature.player.service

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.BundleListRetriever
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import kotlinx.coroutines.guava.await

internal const val ACTION_RETAIN_PLAYBACK_GRANTS =
    "dev.anilbeesetti.nextplayer.RETAIN_PLAYBACK_GRANTS"

/** Give the service its own read grants before the launch activity releases its grants. */
internal fun Context.retainPlaybackUriPermissions(launchIntent: Intent, items: List<MediaItem>) {
    if (launchIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) return
    val uris = items.flatMap { item ->
        item.localConfiguration?.let { configuration ->
            listOf(configuration.uri) + configuration.subtitleConfigurations.map { it.uri }
        }.orEmpty()
    }.filter { it.scheme == ContentResolver.SCHEME_CONTENT }.distinct()
    if (uris.isEmpty()) return
    val grants = ClipData.newRawUri(
        "Playback media",
        uris.first(),
    ).apply {
        uris.drop(1).forEach { addItem(ClipData.Item(it)) }
    }
    checkNotNull(
        startService(
            Intent(
                this,
                PlayerService::class.java,
            )
                .setAction(ACTION_RETAIN_PLAYBACK_GRANTS)
                .setData(uris.first())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .apply { clipData = grants },
        ),
    ) { "Playback service is unavailable" }
}

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
