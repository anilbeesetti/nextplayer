package dev.anilbeesetti.nextplayer.feature.player.service

import dev.anilbeesetti.nextplayer.feature.player.model.DecoderMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecoderSwitcherTest {

    @Test
    fun hwPlus_enablesSystemRenderersOnly() {
        assertTrue(DecoderRendererType.MEDIA_CODEC_VIDEO.isEnabled(DecoderMode.HW_PLUS, DecoderMode.HW_PLUS))
        assertFalse(DecoderRendererType.FFMPEG_VIDEO.isEnabled(DecoderMode.HW_PLUS, DecoderMode.HW_PLUS))
    }

    @Test
    fun hw_enablesSystemRenderersOnly() {
        assertTrue(DecoderRendererType.MEDIA_CODEC_VIDEO.isEnabled(DecoderMode.HW, DecoderMode.HW))
        assertFalse(DecoderRendererType.FFMPEG_VIDEO.isEnabled(DecoderMode.HW, DecoderMode.HW))
    }

    @Test
    fun sw_enablesAppSoftwareRenderersOnly() {
        assertFalse(DecoderRendererType.MEDIA_CODEC_VIDEO.isEnabled(DecoderMode.SW, DecoderMode.SW))
        assertTrue(DecoderRendererType.FFMPEG_VIDEO.isEnabled(DecoderMode.SW, DecoderMode.SW))
    }

    @Test
    fun videoModeChange_doesNotChangeAudioRenderers() {
        assertTrue(DecoderRendererType.FFMPEG_AUDIO.isEnabled(DecoderMode.HW, DecoderMode.SW))
        assertFalse(DecoderRendererType.MEDIA_CODEC_AUDIO.isEnabled(DecoderMode.HW, DecoderMode.SW))
        assertTrue(DecoderRendererType.MEDIA_CODEC_AUDIO.isEnabled(DecoderMode.SW, DecoderMode.HW_PLUS))
        assertFalse(DecoderRendererType.FFMPEG_AUDIO.isEnabled(DecoderMode.SW, DecoderMode.HW_PLUS))
    }

    @Test
    fun onlyHwPlusToHw_requiresDecoderReset() {
        val switcher = DecoderSwitcher(DecoderMode.HW_PLUS)

        assertTrue(switcher.requiresDecoderReset(DecoderMode.HW))
        assertFalse(switcher.requiresDecoderReset(DecoderMode.SW))
        assertFalse(DecoderSwitcher(DecoderMode.HW).requiresDecoderReset(DecoderMode.HW_PLUS))
    }
}
