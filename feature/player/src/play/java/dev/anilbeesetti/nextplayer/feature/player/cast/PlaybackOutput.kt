package dev.anilbeesetti.nextplayer.feature.player.cast

import android.content.Context
import android.widget.Toast
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import dev.anilbeesetti.nextplayer.core.common.Logger
import dev.anilbeesetti.nextplayer.core.ui.R

@UnstableApi
internal class PlaybackOutput(context: Context, localPlayer: ExoPlayer, autoplay: () -> Boolean) {
    private val server = LocalCastServer(
        context,
        DefaultDataSource.Factory(context),
    )
    private val converter = CastMediaItemConverter(
        context,
        server,
    )
    private var transfer: CastTransferCallback? = null
    val player: Player = try {
        val remote = RemoteCastPlayer.Builder(context).setMediaItemConverter(converter).build()
        val transfer = CastTransferCallback(
            remote,
            autoplay,
        ).also { this.transfer = it }
        CastPlayer.Builder(context)
            .setLocalPlayer(localPlayer)
            .setRemotePlayer(remote)
            .setTransferCallback { source, target ->
                try {
                    transfer.transferState(
                        source,
                        target,
                    )
                } catch (error: Exception) {
                    Logger.logError(
                        "Cast",
                        "Playback transfer failed: ${error.message}",
                    )
                    Toast.makeText(
                        context,
                        R.string.cast_transfer_failed,
                        Toast.LENGTH_LONG,
                    ).show()
                    target.clearMediaItems()
                }
            }
            .build()
            .also { output ->
                output.addListener(object : Player.Listener {
                    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                        if (timeline.isEmpty) {
                            server.stop()
                            converter.clear()
                        }
                    }

                    override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
                        if (deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_LOCAL) {
                            server.stop()
                            converter.clear()
                        }
                    }
                })
            }
    } catch (error: RuntimeException) {
        // Devices without Google Play services must retain ordinary local playback.
        Logger.logError(
            "Cast",
            "Cast unavailable: ${error.message}",
        )
        localPlayer
    }

    fun onNewQueue() = transfer?.clearSavedQueue()

    fun release() {
        player.release()
        server.stop()
        converter.clear()
    }
}
