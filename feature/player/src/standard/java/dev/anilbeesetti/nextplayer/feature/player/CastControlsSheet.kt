package dev.anilbeesetti.nextplayer.feature.player

import androidx.compose.runtime.Composable
import androidx.media3.common.Player
import dev.anilbeesetti.nextplayer.feature.player.state.PlaybackSnapshot

@Composable
internal fun CastControlsSheet(
    player: Player,
    snapshot: PlaybackSnapshot,
    title: String,
    output: String,
    onDismiss: () -> Unit,
    onPlayOnPhone: () -> Unit,
    onStop: () -> Unit,
) = Unit
