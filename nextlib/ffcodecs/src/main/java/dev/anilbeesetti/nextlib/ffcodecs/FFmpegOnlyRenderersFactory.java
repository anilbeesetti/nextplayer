package dev.anilbeesetti.nextlib.ffcodecs;

import android.content.Context;
import android.os.Handler;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.audio.AudioCapabilities;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import java.util.ArrayList;

@UnstableApi
public class FFmpegOnlyRenderersFactory extends DefaultRenderersFactory {

    public FFmpegOnlyRenderersFactory(Context context) {
        super(context);
    }

    @Override
    protected void buildVideoRenderers(Context context, int extensionRendererMode, MediaCodecSelector mediaCodecSelector, boolean enableDecoderFallback, Handler eventHandler, VideoRendererEventListener eventListener, long allowedVideoJoiningTimeMs, ArrayList<Renderer> out) {
        out.add(
                new FfmpegVideoRenderer(
                        DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS,
                        eventHandler,
                        eventListener,
                        DefaultRenderersFactory.MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY
                )
        );
    }

    @Override
    protected void buildAudioRenderers(Context context, int extensionRendererMode, MediaCodecSelector mediaCodecSelector, boolean enableDecoderFallback, AudioSink audioSink, Handler eventHandler, AudioRendererEventListener eventListener, ArrayList<Renderer> out) {
        out.add(
                new FfmpegAudioRenderer(eventHandler, eventListener, new DefaultAudioSink.Builder()
                        .setAudioCapabilities(AudioCapabilities.getCapabilities(context))
                        .setEnableFloatOutput(true)
                        .setEnableAudioTrackPlaybackParams(true)
                        .setOffloadMode(DefaultAudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_REQUIRED)
                        .build()
                )
        );
    }
}
