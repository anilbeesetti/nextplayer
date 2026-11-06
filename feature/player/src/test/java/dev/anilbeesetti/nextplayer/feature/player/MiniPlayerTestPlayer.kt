package dev.anilbeesetti.nextplayer.feature.player

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

internal class MiniPlayerTestPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
    private var state = State.Builder()
        .setAvailableCommands(
            Player.Commands.Builder().addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_GET_DEVICE_VOLUME,
                Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
            ).build(),
        )
        .setPlaylist(
            listOf(
                item(0),
                item(1),
            ),
        )
        .setPlaybackState(Player.STATE_READY)
        .setContentPositionMs(10_000)
        .setDeviceVolume(25)
        .build()

    fun item(index: Int, durationUs: Long = 60_000_000): MediaItemData =
        MediaItemData.Builder(index)
            .setMediaItem(
                MediaItem.Builder().setMediaId("$index").setUri("file:///$index.mp4").build(),
            )
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Movie ${index + 1}").build())
            .setDurationUs(durationUs)
            .build()

    fun update(change: State.Builder.() -> Unit) {
        state = state.buildUpon().apply(change).build()
        invalidateState()
    }

    fun remote(remote: Boolean) = update {
        setDeviceInfo(
            DeviceInfo.Builder(
                if (remote) DeviceInfo.PLAYBACK_TYPE_REMOTE else DeviceInfo.PLAYBACK_TYPE_LOCAL,
            ).setMaxVolume(100).build(),
        )
    }

    override fun getState(): State = state
    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        state = state.buildUpon().setPlayWhenReady(
            playWhenReady,
            1,
        ).build()
        return Futures.immediateVoidFuture()
    }
    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        state = state.buildUpon().setCurrentMediaItemIndex(mediaItemIndex)
            .setContentPositionMs(if (positionMs == C.TIME_UNSET) 0 else positionMs).build()
        return Futures.immediateVoidFuture()
    }
    override fun handleSetDeviceVolume(deviceVolume: Int, flags: Int): ListenableFuture<*> {
        state = state.buildUpon().setDeviceVolume(deviceVolume).build()
        return Futures.immediateVoidFuture()
    }
}
