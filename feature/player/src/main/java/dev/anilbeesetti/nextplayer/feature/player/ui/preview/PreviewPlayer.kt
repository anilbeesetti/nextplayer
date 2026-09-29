package dev.anilbeesetti.nextplayer.feature.player.ui.preview

import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
@Composable
internal fun rememberPreviewPlayer(): Player = remember {
    object : SimpleBasePlayer(Looper.getMainLooper()) {
        private val previewState = State.Builder()
            .setAvailableCommands(
                Player.Commands.Builder()
                    .addAll(Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_METADATA, Player.COMMAND_PLAY_PAUSE, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build(),
            )
            .setPlaylist(
                listOf(
                    MediaItemData.Builder("preview")
                        .setMediaItem(
                            MediaItem.Builder()
                                .setMediaId("preview")
                                .setMediaMetadata(MediaMetadata.Builder().setTitle("Sample video").build())
                                .build(),
                        )
                        .setDurationUs(120_000_000)
                        .build(),
                    MediaItemData.Builder("preview2")
                        .setMediaItem(
                            MediaItem.Builder()
                                .setMediaId("preview2")
                                .setMediaMetadata(MediaMetadata.Builder().setTitle("Sample video").build())
                                .build(),
                        )
                        .setDurationUs(120_000_000)
                        .build(),
                ),
            )
            .setContentPositionMs(30_000)
            .build()

        override fun getState(): State = previewState
    }
}
