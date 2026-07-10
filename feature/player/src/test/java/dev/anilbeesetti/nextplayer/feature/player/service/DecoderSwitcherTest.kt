package dev.anilbeesetti.nextplayer.feature.player.service

import dev.anilbeesetti.nextplayer.feature.player.model.DecoderMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecoderSwitcherTest {

    @Test
    fun hwPlus_enablesSystemRenderersOnly() {
        assertTrue(DecoderRendererType.MEDIA_CODEC_VIDEO.isEnabled(DecoderMode.HW_PLUS, false))
        assertFalse(DecoderRendererType.FFMPEG_VIDEO.isEnabled(DecoderMode.HW_PLUS, false))
    }

    @Test
    fun hw_enablesSystemRenderersOnly() {
        assertTrue(DecoderRendererType.MEDIA_CODEC_VIDEO.isEnabled(DecoderMode.HW, false))
        assertFalse(DecoderRendererType.FFMPEG_VIDEO.isEnabled(DecoderMode.HW, false))
    }

    @Test
    fun sw_enablesAppSoftwareRenderersOnly() {
        assertFalse(DecoderRendererType.MEDIA_CODEC_VIDEO.isEnabled(DecoderMode.SW, false))
        assertTrue(DecoderRendererType.FFMPEG_VIDEO.isEnabled(DecoderMode.SW, false))
    }

    @Test
    fun audioRenderer_followsSelectedMode() {
        assertTrue(DecoderRendererType.MEDIA_CODEC_AUDIO.isEnabled(DecoderMode.HW_PLUS, false))
        assertTrue(DecoderRendererType.MEDIA_CODEC_AUDIO.isEnabled(DecoderMode.HW, false))
        assertFalse(DecoderRendererType.FFMPEG_AUDIO.isEnabled(DecoderMode.HW, false))

        assertTrue(DecoderRendererType.FFMPEG_AUDIO.isEnabled(DecoderMode.SW, false))
        assertFalse(DecoderRendererType.MEDIA_CODEC_AUDIO.isEnabled(DecoderMode.SW, false))
    }

    @Test
    fun hwPlusAudioFallback_enablesSystemAudioRenderer() {
        assertTrue(DecoderRendererType.FFMPEG_AUDIO.isEnabled(DecoderMode.SW, true))
        assertTrue(DecoderRendererType.MEDIA_CODEC_AUDIO.isEnabled(DecoderMode.SW, true))
    }

    @Test
    fun onlyHwPlusToHw_requiresDecoderReset() {
        val switcher = DecoderSwitcher(DecoderMode.HW_PLUS, useHwPlusAudioFallback = true)

        assertTrue(switcher.requiresDecoderReset(DecoderMode.HW))
        assertFalse(switcher.requiresDecoderReset(DecoderMode.SW))
        assertFalse(
            DecoderSwitcher(DecoderMode.HW, useHwPlusAudioFallback = true)
                .requiresDecoderReset(DecoderMode.HW_PLUS),
        )
    }
}
