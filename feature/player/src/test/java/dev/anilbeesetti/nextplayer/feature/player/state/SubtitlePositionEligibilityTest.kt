package dev.anilbeesetti.nextplayer.feature.player.state

import android.graphics.Bitmap
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.Cue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SubtitlePositionEligibilityTest {
    @Test
    fun assCannotMoveEvenDuringCueGapsOrWithTextFallback() {
        assertFalse(
            canRepositionSubtitles(
                Format.Builder().setSampleMimeType(MimeTypes.TEXT_SSA).build(),
                emptyList(),
            ),
        )
        assertFalse(
            canRepositionSubtitles(
                Format.Builder().setSampleMimeType(MimeTypes.TEXT_SSA).build(),
                listOf(Cue.Builder().setText("ASS").build()),
            ),
        )
    }

    @Test
    fun transcodedAssFallbackStillCannotMove() {
        val format = Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_MEDIA3_CUES)
            .setCodecs(MimeTypes.TEXT_SSA).build()
        assertFalse(
            canRepositionSubtitles(
                format,
                emptyList(),
            ),
        )
        assertTrue(
            canRepositionSubtitles(
                format.buildUpon().setCodecs(MimeTypes.APPLICATION_SUBRIP).build(),
                emptyList(),
            ),
        )
    }

    @Test
    fun bitmapLayoutsCannotMoveButOrdinaryTextCan() {
        val bitmap = Bitmap.createBitmap(
            2,
            2,
            Bitmap.Config.ARGB_8888,
        )
        assertFalse(
            canRepositionSubtitles(
                null,
                listOf(Cue.Builder().setBitmap(bitmap).build()),
            ),
        )
        assertTrue(
            canRepositionSubtitles(
                Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_SUBRIP).build(),
                listOf(Cue.Builder().setText("SRT").build()),
            ),
        )
        assertTrue(
            canRepositionSubtitles(
                Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).build(),
                emptyList(),
            ),
        )
    }
}
