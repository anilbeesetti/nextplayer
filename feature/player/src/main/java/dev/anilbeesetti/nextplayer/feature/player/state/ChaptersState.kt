package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.Chapter
import androidx.media3.ui.compose.state.PlayerStateObserver
import androidx.media3.ui.compose.state.ProgressStateWithTickInterval
import androidx.media3.ui.compose.state.observeState


@OptIn(UnstableApi::class)
@Composable
fun rememberChaptersState(player: Player?, progressState: ProgressStateWithTickInterval): ChaptersState {
    val chaptersState = remember(player, progressState) { ChaptersState(player, progressState) }
    LaunchedEffect(player, progressState) { chaptersState.observe() }
    return chaptersState
}


@UnstableApi
class ChaptersState(
    private val player: Player?,
    private val progressState: ProgressStateWithTickInterval,
) {
    var chapters: List<Chapter> by mutableStateOf(emptyList())
        private set

    val currentChapterIndex: Int by derivedStateOf { chapters.currentChapterIndex(progressState.currentPositionMs) }

    private val playerStateObserver: PlayerStateObserver? =
        player?.observeState(
            Player.EVENT_TRACKS_CHANGED,
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_POSITION_DISCONTINUITY,
            Player.EVENT_PLAYBACK_STATE_CHANGED,
        ) {
            updateChapters()
        }

    suspend fun observe() {
        updateChapters()
        playerStateObserver?.observe()
    }

    private fun updateChapters() {
        chapters = player?.let {
            if (player.currentTimeline.isEmpty) {
                emptyList()
            } else {
                val periodOffsetMs = player.currentTimeline.getPeriod(player.currentPeriodIndex, Timeline.Period()).positionInWindowMs
                player.currentTracks.chapters(player.duration, periodOffsetMs)
            }
        } ?: emptyList()
    }
}

@OptIn(UnstableApi::class)
private fun Tracks.chapters(durationMs: Long, periodOffsetMs: Long = 0): List<Chapter> {
    if (durationMs <= 0) return emptyList()
    return groups.flatMap { group ->
        (0 until group.length).flatMap { trackIndex ->
            group.getTrackFormat(trackIndex).metadata?.getEntriesOfType(Chapter::class.java).orEmpty()
        }
    }.mapNotNull { chapter ->
        if (chapter.isHidden || chapter.startTimeMs < 0) return@mapNotNull null
        val start = chapter.startTimeMs + periodOffsetMs
        if (start < 0 && periodOffsetMs >= 0) return@mapNotNull null
        val end = if (chapter.endTimeMs == C.TIME_UNSET) C.TIME_UNSET else chapter.endTimeMs + periodOffsetMs
        if (start >= durationMs || (end != C.TIME_UNSET && end <= 0)) return@mapNotNull null
        if (periodOffsetMs == 0L) {
            chapter
        } else {
            Chapter.Builder()
                .setStartTimeMs(start.coerceAtLeast(0))
                .setEndTimeMs(end)
                .setTitle(chapter.title)
                .build()
        }
    }.sortedBy { it.startTimeMs }.distinctBy { it.startTimeMs }
}

@OptIn(UnstableApi::class)
internal fun List<Chapter>.currentChapterIndex(positionMs: Long): Int =
    indexOfLast { it.startTimeMs <= positionMs }
