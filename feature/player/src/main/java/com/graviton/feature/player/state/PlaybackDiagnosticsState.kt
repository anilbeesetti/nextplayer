package com.graviton.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.graviton.feature.player.decoder.PlaybackDiagnosticsSnapshot
import com.graviton.feature.player.service.getPlaybackDiagnostics
import kotlinx.coroutines.delay

/**
 * Decoder facts for the Video information sheet.
 *
 * The service is only polled while [enabled] is true - that is, while the information sheet is
 * actually on screen - so ordinary playback pays nothing for this. Values only refresh at a slow
 * cadence because dropped-frame counts are the only member that moves during playback.
 */
@UnstableApi
@Composable
fun rememberPlaybackDiagnosticsState(
    player: Player,
    enabled: Boolean,
): PlaybackDiagnosticsSnapshot {
    var snapshot by remember(player) { mutableStateOf(PlaybackDiagnosticsSnapshot()) }

    LaunchedEffect(player, enabled) {
        if (!enabled) return@LaunchedEffect
        val controller = player as? MediaController ?: return@LaunchedEffect
        while (true) {
            snapshot = runCatching { controller.getPlaybackDiagnostics() }.getOrDefault(snapshot)
            delay(REFRESH_INTERVAL_MS)
        }
    }

    return snapshot
}

private const val REFRESH_INTERVAL_MS = 1_500L
