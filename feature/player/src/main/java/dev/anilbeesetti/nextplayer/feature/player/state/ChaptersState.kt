package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.Chapter

@OptIn(UnstableApi::class)
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

@OptIn(UnstableApi::class)
internal fun List<Chapter>.currentChapterIndex(positionMs: Long): Int =
    indexOfLast { it.startTimeMs <= positionMs }
