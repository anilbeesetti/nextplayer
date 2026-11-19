package dev.anilbeesetti.nextplayer.feature.player.cast

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CastSubtitlesTest {
    @Test
    fun subRipBecomesWebVttWithTimingUnicodeAndEscapedText() {
        val srt = "1\n00:00:01,250 --> 00:00:03,500\n<b>Héllo</b> & goodbye\nSecond line\n\n" +
            "2\n01:02:03,004 --> 01:02:04,005\nLater\n"
        assertEquals(
            "WEBVTT\n\n00:00:01.250 --> 00:00:03.500\nHéllo &amp; goodbye\nSecond line\n\n" +
                "01:02:03.004 --> 01:02:04.005\nLater\n\n",
            castWebVtt(
                srt.toByteArray(),
                MimeTypes.APPLICATION_SUBRIP,
            ).toString(Charsets.UTF_8),
        )
    }

    @Test
    fun emptySubtitlesProduceValidHeader() {
        assertEquals(
            "WEBVTT\n\n",
            castWebVtt(
                byteArrayOf(),
                MimeTypes.APPLICATION_SUBRIP,
            ).toString(Charsets.UTF_8),
        )
    }
}
