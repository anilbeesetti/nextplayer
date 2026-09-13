package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.Chapter

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun rememberChapters(player: Player): List<Chapter> {
    var chapters by remember(player) { mutableStateOf(player.readChapters()) }
    LaunchedEffect(player) {
        chapters = player.readChapters()
        player.listen { events ->
            if (events.containsAny(
                    Player.EVENT_TRACKS_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_POSITION_DISCONTINUITY,
                )
            ) {
                chapters = player.readChapters()
            }
        }
    }
    return chapters
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun Player.readChapters(): List<Chapter> {
    if (currentTimeline.isEmpty) return emptyList()
    val periodOffsetMs = currentTimeline.getPeriod(currentPeriodIndex, Timeline.Period()).positionInWindowMs
    return currentTracks.chapters(duration, periodOffsetMs)
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun Tracks.chapters(durationMs: Long, periodOffsetMs: Long = 0): List<Chapter> {
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

@androidx.annotation.OptIn(UnstableApi::class)
internal fun List<Chapter>.currentChapterIndex(positionMs: Long): Int =
    indexOfLast { it.startTimeMs <= positionMs }
