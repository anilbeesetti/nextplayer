package dev.anilbeesetti.nextplayer.feature.player

import android.content.res.Configuration
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.core.ui.preview.DayNightPreview
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackConnection
import dev.anilbeesetti.nextplayer.feature.player.state.PlaybackSnapshot
import dev.anilbeesetti.nextplayer.feature.player.ui.PlaybackDismissUndoBar
import dev.anilbeesetti.nextplayer.feature.player.ui.preview.rememberPreviewPlayer

internal data class PlaybackPreviewCase(
    val name: String,
    val snapshot: PlaybackSnapshot,
    val connection: PlaybackConnection = PlaybackConnection.LOCAL,
    val deviceName: String? = null,
    val controlsEnabled: Boolean = true,
)

private const val DEVICE_NAME = "Living Room TV"

private val allCommands = Player.Commands.Builder().addAll(
    Player.COMMAND_PLAY_PAUSE,
    Player.COMMAND_SEEK_TO_PREVIOUS,
    Player.COMMAND_SEEK_TO_NEXT,
    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
    Player.COMMAND_SEEK_TO_MEDIA_ITEM,
    Player.COMMAND_GET_TIMELINE,
    Player.COMMAND_SET_SHUFFLE_MODE,
    Player.COMMAND_SET_REPEAT_MODE,
).build()

@OptIn(UnstableApi::class)
private fun previewSnapshot(
    title: String? = "Sample video",
    uri: String? = "file:///storage/emulated/0/Movies/Holidays/sample.mp4",
    playing: Boolean = true,
    remote: Boolean = false,
    shuffle: Boolean = false,
    repeatMode: Int = Player.REPEAT_MODE_OFF,
    commands: Player.Commands = allCommands,
): PlaybackSnapshot {
    val metadata = MediaMetadata.Builder().setTitle(title).build()
    return PlaybackSnapshot(
        item = MediaItem.Builder().setMediaId("preview").setUri(uri).setMediaMetadata(metadata)
            .build(),
        metadata = metadata,
        playbackState = Player.STATE_READY,
        playWhenReady = playing,
        deviceInfo = DeviceInfo.Builder(
            if (remote) DeviceInfo.PLAYBACK_TYPE_REMOTE else DeviceInfo.PLAYBACK_TYPE_LOCAL,
        ).setMaxVolume(100).build(),
        duration = 120_000,
        position = 30_000,
        commands = commands,
        deviceVolume = 40,
        volume = 1f,
        shuffle = shuffle,
        repeatMode = repeatMode,
    )
}

// STOPPED is left out: the overlay renders nothing once the session has stopped.
internal val playbackPreviewCases = listOf(
    PlaybackPreviewCase(
        "Playing",
        previewSnapshot(),
    ),
    PlaybackPreviewCase(
        "Paused",
        previewSnapshot(playing = false),
    ),
    PlaybackPreviewCase(
        "Shuffle and repeat all",
        previewSnapshot(
            shuffle = true,
            repeatMode = Player.REPEAT_MODE_ALL,
        ),
    ),
    PlaybackPreviewCase(
        "Repeat one",
        previewSnapshot(repeatMode = Player.REPEAT_MODE_ONE),
    ),
    PlaybackPreviewCase(
        "Limited commands",
        previewSnapshot(
            commands = Player.Commands.Builder().add(Player.COMMAND_PLAY_PAUSE).build(),
        ),
    ),
    PlaybackPreviewCase(
        "Missing metadata",
        previewSnapshot(
            title = null,
            uri = null,
        ),
    ),
    PlaybackPreviewCase(
        "Long text",
        previewSnapshot(
            title = "A very long video title that cannot fit on one line of the player",
            uri = "file:///storage/emulated/0/Movies/A very long folder name for videos/a.mp4",
        ),
    ),
    PlaybackPreviewCase(
        "Connecting",
        previewSnapshot(playing = false),
        PlaybackConnection.CONNECTING,
        DEVICE_NAME,
        controlsEnabled = false,
    ),
    PlaybackPreviewCase(
        "Casting",
        previewSnapshot(remote = true),
        PlaybackConnection.CAST,
        DEVICE_NAME,
    ),
    PlaybackPreviewCase(
        "Casting paused",
        previewSnapshot(
            playing = false,
            remote = true,
        ),
        PlaybackConnection.CAST,
        DEVICE_NAME,
    ),
    PlaybackPreviewCase(
        "Connection lost",
        previewSnapshot(playing = false),
        PlaybackConnection.LOST,
        DEVICE_NAME,
        controlsEnabled = false,
    ),
)

private val longTextCase = playbackPreviewCases.first { it.name == "Long text" }

internal class PlaybackPreviewCaseProvider : PreviewParameterProvider<PlaybackPreviewCase> {
    override val values = playbackPreviewCases.asSequence()

    override fun getDisplayName(index: Int): String = playbackPreviewCases[index].name
}

@Composable
internal fun MiniPlayerPreviewContent(case: PlaybackPreviewCase) {
    NextPlayerTheme {
        Surface {
            MiniPlayer(
                player = rememberPreviewPlayer(),
                onOpenPlayer = {},
                onStop = {},
                modifier = Modifier.padding(16.dp),
                controlsEnabled = case.controlsEnabled,
                snapshot = case.snapshot,
                secondaryText = playbackSecondaryText(
                    case.snapshot,
                    case.connection,
                    case.deviceName,
                ),
                connecting = case.connection == PlaybackConnection.CONNECTING,
                artworkPainter = painterResource(R.drawable.artwork_default),
            )
        }
    }
}

@Composable
internal fun ExpandedPlaybackPreviewContent(case: PlaybackPreviewCase, landscape: Boolean) {
    NextPlayerTheme {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            ExpandedPlaybackControls(
                player = rememberPreviewPlayer(),
                snapshot = case.snapshot,
                title = playbackTitle(case.snapshot),
                secondary = playbackSecondaryText(
                    case.snapshot,
                    case.connection,
                    case.deviceName,
                ),
                controlsEnabled = case.controlsEnabled,
                landscape = landscape,
                connection = case.connection,
                deviceName = case.deviceName,
                onCollapse = {},
                onQueue = {},
                onOutput = {},
                onStop = {},
                onWatchVideo = {},
                onCommand = {},
                dragModifier = Modifier,
                artworkPainter = painterResource(R.drawable.artwork_default),
            )
        }
    }
}

// A locale alone does not mirror layout in a library module without supportsRtl.
@Composable
private fun RtlPreview(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        content,
    )
}

@DayNightPreview
@Composable
private fun MiniPlayerStatesPreview(
    @PreviewParameter(PlaybackPreviewCaseProvider::class) case: PlaybackPreviewCase,
) {
    MiniPlayerPreviewContent(case)
}

@Preview(
    name = "RTL",
    locale = "ar",
)
@Composable
private fun MiniPlayerRtlPreview() {
    RtlPreview { MiniPlayerPreviewContent(longTextCase) }
}

@Preview(
    name = "Large text",
    fontScale = 2f,
)
@Composable
private fun MiniPlayerLargeTextPreview() {
    MiniPlayerPreviewContent(longTextCase)
}

@DayNightPreview
@Composable
private fun MiniPlayerDismissUndoPreview() {
    NextPlayerTheme {
        Surface {
            PlaybackDismissUndoBar(
                deadline = SystemClock.elapsedRealtime() + 2_000,
                onUndo = {},
                onClose = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(
    name = "Portrait Day",
    widthDp = 411,
    heightDp = 891,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Preview(
    name = "Portrait Night",
    widthDp = 411,
    heightDp = 891,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ExpandedPlaybackPortraitPreview(
    @PreviewParameter(PlaybackPreviewCaseProvider::class) case: PlaybackPreviewCase,
) {
    ExpandedPlaybackPreviewContent(
        case,
        landscape = false,
    )
}

@Preview(
    name = "Landscape Day",
    widthDp = 891,
    heightDp = 411,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Preview(
    name = "Landscape Night",
    widthDp = 891,
    heightDp = 411,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ExpandedPlaybackLandscapePreview(
    @PreviewParameter(PlaybackPreviewCaseProvider::class) case: PlaybackPreviewCase,
) {
    ExpandedPlaybackPreviewContent(
        case,
        landscape = true,
    )
}

@Preview(
    name = "RTL",
    widthDp = 411,
    heightDp = 891,
    locale = "ar",
)
@Composable
private fun ExpandedPlaybackRtlPreview() {
    RtlPreview {
        ExpandedPlaybackPreviewContent(
            longTextCase,
            landscape = false,
        )
    }
}

@Preview(
    name = "Large text",
    widthDp = 411,
    heightDp = 891,
    fontScale = 2f,
)
@Composable
private fun ExpandedPlaybackLargeTextPreview() {
    ExpandedPlaybackPreviewContent(
        longTextCase,
        landscape = false,
    )
}
