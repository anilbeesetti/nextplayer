package com.graviton.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@UnstableApi
@Composable
fun rememberCutSegmentState(player: Player): CutSegmentState {
    val cutSegmentState = remember(player) { CutSegmentState(player) }
    LaunchedEffect(player) { cutSegmentState.observe() }
    return cutSegmentState
}

/**
 * An A-B segment of the current media item.
 *
 * The segment is enforced by seeking back to [startMs] when playback passes [endMs]. The watcher
 * loop only runs while a complete segment exists and playback is active, so the default player has
 * no extra polling at all.
 */
@Stable
class CutSegmentState(private val player: Player) {

    var startMs: Long? by mutableStateOf(null)
        private set

    var endMs: Long? by mutableStateOf(null)
        private set

    var loopEnabled: Boolean by mutableStateOf(true)
        private set

    val hasSegment: Boolean get() = startMs != null && endMs != null

    fun setStartToCurrentPosition() {
        val position = player.currentPosition.coerceAtLeast(0L)
        startMs = position
        // Keep the segment ordered; an end before the new start is no longer meaningful.
        endMs?.let { if (it <= position) endMs = null }
    }

    fun setEndToCurrentPosition() {
        val position = player.currentPosition.coerceAtLeast(0L)
        val start = startMs
        if (start != null && position <= start) return
        endMs = position
    }

    fun setLoopEnabled(enabled: Boolean) {
        loopEnabled = enabled
    }

    fun clear() {
        startMs = null
        endMs = null
    }

    fun jumpToStart() {
        startMs?.let { player.seekTo(it) }
    }

    suspend fun observe() {
        coroutineScope {
            launch {
                // A new media item invalidates positions taken from the previous one.
                player.listen { events ->
                    if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                        clear()
                    }
                }
            }

            // The watcher only exists while a complete segment is armed. Without a segment this
            // collector is idle, so the default player gains no polling.
            snapshotFlow { Triple(startMs, endMs, loopEnabled) }
                .collectLatest { (start, end, loop) ->
                    if (start == null || end == null || !loop) return@collectLatest
                    while (true) {
                        delay(SEGMENT_WATCH_INTERVAL_MS)
                        if (player.isPlaying && player.currentPosition >= end) player.seekTo(start)
                    }
                }
        }
    }

    private companion object {
        const val SEGMENT_WATCH_INTERVAL_MS = 200L
    }
}
