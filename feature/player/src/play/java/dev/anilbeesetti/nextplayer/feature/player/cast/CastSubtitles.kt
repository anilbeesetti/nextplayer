package dev.anilbeesetti.nextplayer.feature.player.cast

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import java.util.Locale

/** Cast accepts WebVTT, not SRT/ASS. Keep their text and timing, without local styling. */
@UnstableApi
internal fun castWebVtt(data: ByteArray, mimeType: String): ByteArray {
    val parser = DefaultSubtitleParserFactory().create(
        Format.Builder().setSampleMimeType(mimeType).build(),
    )
    val cues = mutableListOf<CuesWithTiming>()
    parser.parse(
        data,
        SubtitleParser.OutputOptions.allCues(),
    ) { cues.add(it) }
    return buildString {
        append("WEBVTT\n\n")
        for (timed in cues.sortedBy { it.startTimeUs }) {
            if (timed.startTimeUs == C.TIME_UNSET || timed.durationUs <= 0) continue
            val text = timed.cues.mapNotNull { it.text }.joinToString("\n").trim()
            if (text.isEmpty()) continue
            append(timestamp(timed.startTimeUs))
            append(" --> ")
            append(timestamp(timed.endTimeUs))
            append('\n')
            append(
                text.replace(
                    "&",
                    "&amp;",
                ).replace(
                    "<",
                    "&lt;",
                ).replace(
                    ">",
                    "&gt;",
                ),
            )
            append("\n\n")
        }
    }.toByteArray(Charsets.UTF_8)
}

private fun timestamp(timeUs: Long): String {
    val ms = (timeUs / 1000).coerceAtLeast(0)
    return String.format(
        Locale.ROOT,
        "%02d:%02d:%02d.%03d",
        ms / 3_600_000,
        ms / 60_000 % 60,
        ms / 1000 % 60,
        ms % 1000,
    )
}
