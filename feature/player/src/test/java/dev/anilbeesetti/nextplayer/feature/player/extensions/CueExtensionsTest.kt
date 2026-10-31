package dev.anilbeesetti.nextplayer.feature.player.extensions

import androidx.media3.common.text.Cue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CueExtensionsTest {

    @Test
    fun `empty cue list remains empty`() {
        assertEquals(emptyList<Cue>(), emptyList<Cue>().stackUnpositionedCues())
    }

    @Test
    fun `single unpositioned cue remains unchanged`() {
        val cue = textCue("only cue")

        val result = listOf(cue).stackUnpositionedCues()

        assertSame(cue, result.single())
        assertEquals(Cue.DIMEN_UNSET, result.single().line)
    }

    @Test
    fun `two unpositioned cues are stacked from the bottom`() {
        val result = listOf(textCue("first"), textCue("second")).stackUnpositionedCues()

        assertEquals(-1f, result[0].line)
        assertEquals(Cue.LINE_TYPE_NUMBER, result[0].lineType)
        assertEquals(-2f, result[1].line)
        assertEquals(Cue.LINE_TYPE_NUMBER, result[1].lineType)
    }

    @Test
    fun `multiline lower cue offsets the cue above by its rendered line count`() {
        val result = listOf(textCue("a\nb"), textCue("above")).stackUnpositionedCues()

        assertEquals(-1f, result[0].line)
        assertEquals(-3f, result[1].line)
    }

    @Test
    fun `explicitly positioned cue remains unchanged while siblings are stacked`() {
        val first = textCue("first")
        val positioned = Cue.Builder()
            .setText("positioned")
            .setLine(0.25f, Cue.LINE_TYPE_FRACTION)
            .build()
        val second = textCue("second")

        val result = listOf(first, positioned, second).stackUnpositionedCues()

        assertEquals(-1f, result[0].line)
        assertSame(positioned, result[1])
        assertEquals(0.25f, result[1].line)
        assertEquals(Cue.LINE_TYPE_FRACTION, result[1].lineType)
        assertEquals(-2f, result[2].line)
    }

    @Test
    fun `stacking preserves cue text identity`() {
        val cue = textCue("styled")

        val result = listOf(cue, textCue("second")).stackUnpositionedCues()

        assertSame(cue.text, result[0].text)
    }

    private fun textCue(text: CharSequence): Cue = Cue.Builder().setText(text).build()
}
