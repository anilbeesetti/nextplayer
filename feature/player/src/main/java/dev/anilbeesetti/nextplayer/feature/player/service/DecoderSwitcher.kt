package dev.anilbeesetti.nextplayer.feature.player.service

import android.content.Context
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.video.VideoRendererEventListener
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderMode
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory

/**
 * Switches video decoders without replacing the [ExoPlayer] instance.
 *
 * All system and FFmpeg renderers are created once. A mode change updates their reported
 * capabilities and disabled state so Media3 remaps the video track to the requested renderer.
 * Audio keeps the mode selected when this switcher is created and is never changed at runtime.
 */
@UnstableApi
internal class DecoderSwitcher(initialMode: DecoderMode) {

    private val mediaCodecSelector = SwitchableMediaCodecSelector(initialMode)
    private val managedRenderers = mutableListOf<ManagedRenderer>()
    private val fixedAudioMode = initialMode

    @Volatile
    var mode: DecoderMode = initialMode
        private set

    fun createRenderersFactory(context: Context): DefaultRenderersFactory {
        return DecoderRenderersFactory(
            context = context,
            mediaCodecSelector = mediaCodecSelector,
            wrapRenderer = ::wrapRenderer,
        ).apply {
            setEnableDecoderFallback(true)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        }
    }

    fun requiresDecoderReset(newMode: DecoderMode): Boolean {
        return mode == DecoderMode.HW_PLUS && newMode == DecoderMode.HW
    }

    /**
     * Applies [newMode] to video while preserving the playlist, position, and audio renderer.
     *
     * HW+ to HW also stops and prepares the same player so MediaCodec drops a possible system
     * software codec and queries the hardware-only codec list again.
     */
    fun switchTo(
        newMode: DecoderMode,
        player: ExoPlayer,
        trackSelector: DefaultTrackSelector,
    ) {
        if (newMode == mode) return

        val playWhenReady = player.playWhenReady
        val shouldResetDecoder = requiresDecoderReset(newMode)

        mode = newMode
        mediaCodecSelector.mode = newMode

        if (shouldResetDecoder) {
            val shouldPrepare = player.mediaItemCount > 0
            player.stop()
            apply(trackSelector)
            if (shouldPrepare) {
                player.prepare()
                player.playWhenReady = playWhenReady
            }
        } else {
            apply(trackSelector)
        }
    }

    fun apply(trackSelector: DefaultTrackSelector) {
        val parameters = trackSelector.buildUponParameters()
        managedRenderers.forEach { renderer ->
            parameters.setRendererDisabled(
                renderer.index,
                !renderer.type.isEnabled(mode, fixedAudioMode),
            )
        }
        trackSelector.setParameters(parameters)
    }

    private fun wrapRenderer(index: Int, renderer: Renderer): Renderer {
        val rendererType = DecoderRendererType.fromName(renderer.name) ?: return renderer
        managedRenderers += ManagedRenderer(index, rendererType)
        return ModeAwareRenderer(
            delegate = renderer,
            isEnabled = { rendererType.isEnabled(mode, fixedAudioMode) },
        )
    }
}

private data class ManagedRenderer(
    val index: Int,
    val type: DecoderRendererType,
)

internal enum class DecoderRendererType(val rendererName: String) {
    MEDIA_CODEC_VIDEO(MEDIA_CODEC_VIDEO_RENDERER),
    FFMPEG_VIDEO(FFMPEG_VIDEO_RENDERER),
    MEDIA_CODEC_AUDIO(MEDIA_CODEC_AUDIO_RENDERER),
    FFMPEG_AUDIO(FFMPEG_AUDIO_RENDERER),
    ;

    fun isEnabled(videoMode: DecoderMode, audioMode: DecoderMode): Boolean {
        return when (this) {
            MEDIA_CODEC_VIDEO -> videoMode != DecoderMode.SW
            FFMPEG_VIDEO -> videoMode == DecoderMode.SW
            MEDIA_CODEC_AUDIO -> audioMode != DecoderMode.SW
            FFMPEG_AUDIO -> audioMode == DecoderMode.SW
        }
    }

    companion object {
        fun fromName(rendererName: String): DecoderRendererType? {
            return entries.find { it.rendererName == rendererName }
        }
    }
}

/** Makes an inactive renderer look unsupported before Media3 maps tracks to renderers. */
@UnstableApi
private class ModeAwareRenderer(
    delegate: Renderer,
    isEnabled: () -> Boolean,
) : ForwardingRenderer(delegate) {

    private val modeAwareCapabilities = ModeAwareCapabilities(
        delegate = delegate.capabilities,
        isEnabled = isEnabled,
    )

    override fun getCapabilities(): RendererCapabilities = modeAwareCapabilities
}

@UnstableApi
private class ModeAwareCapabilities(
    private val delegate: RendererCapabilities,
    private val isEnabled: () -> Boolean,
) : RendererCapabilities {
    override fun getName(): String = delegate.name

    override fun getTrackType(): Int = delegate.trackType

    override fun supportsFormat(format: Format): Int {
        return if (isEnabled()) {
            delegate.supportsFormat(format)
        } else {
            RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
        }
    }

    override fun supportsMixedMimeTypeAdaptation(): Int {
        return delegate.supportsMixedMimeTypeAdaptation()
    }

    override fun setListener(listener: RendererCapabilities.Listener) {
        delegate.setListener(listener)
    }

    override fun clearListener() {
        delegate.clearListener()
    }
}

@UnstableApi
private class SwitchableMediaCodecSelector(initialMode: DecoderMode) : MediaCodecSelector {
    @Volatile
    var mode: DecoderMode = initialMode

    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean,
    ) = MediaCodecSelector.DEFAULT.getDecoderInfos(
        mimeType,
        requiresSecureDecoder,
        requiresTunnelingDecoder,
    ).let { decoderInfos ->
        if (mode == DecoderMode.HW && MimeTypes.isVideo(mimeType)) {
            decoderInfos.filter { it.hardwareAccelerated }
        } else {
            decoderInfos
        }
    }
}

@UnstableApi
private class DecoderRenderersFactory(
    context: Context,
    mediaCodecSelector: MediaCodecSelector,
    private val wrapRenderer: (Int, Renderer) -> Renderer,
) : NextRenderersFactory(context) {

    init {
        setMediaCodecSelector(mediaCodecSelector)
    }

    override fun createRenderers(
        eventHandler: Handler,
        videoRendererEventListener: VideoRendererEventListener,
        audioRendererEventListener: AudioRendererEventListener,
        textRendererOutput: TextOutput,
        metadataRendererOutput: MetadataOutput,
    ): Array<Renderer> {
        return super.createRenderers(
            eventHandler,
            videoRendererEventListener,
            audioRendererEventListener,
            textRendererOutput,
            metadataRendererOutput,
        ).mapIndexed(wrapRenderer).toTypedArray()
    }
}

private const val MEDIA_CODEC_VIDEO_RENDERER = "MediaCodecVideoRenderer"
private const val MEDIA_CODEC_AUDIO_RENDERER = "MediaCodecAudioRenderer"
private const val FFMPEG_VIDEO_RENDERER = "FfmpegVideoRenderer"
private const val FFMPEG_AUDIO_RENDERER = "FfmpegAudioRenderer"
