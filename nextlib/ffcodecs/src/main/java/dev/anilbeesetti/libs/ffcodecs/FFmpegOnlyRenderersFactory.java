package dev.anilbeesetti.libs.ffcodecs;

import static androidx.media3.exoplayer.DefaultRenderersFactory.DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS;

import android.content.Context;
import android.os.Handler;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.audio.AudioCapabilities;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.metadata.MetadataOutput;
import androidx.media3.exoplayer.text.TextOutput;
import androidx.media3.exoplayer.video.VideoRendererEventListener;

import java.util.ArrayList;

@UnstableApi
public class FFmpegOnlyRenderersFactory implements RenderersFactory {

    private final Context context;

    public FFmpegOnlyRenderersFactory(Context context) {
        this.context = context;
    }

    @Override
    public Renderer[] createRenderers(Handler eventHandler, VideoRendererEventListener videoRendererEventListener, AudioRendererEventListener audioRendererEventListener, TextOutput textRendererOutput, MetadataOutput metadataRendererOutput) {
        ArrayList<Renderer> renderersList = new ArrayList<>();
        renderersList.add(
                new FfmpegVideoRenderer(
                        DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS,
                        eventHandler,
                        videoRendererEventListener,
                        DefaultRenderersFactory.MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY
                )
        );

        renderersList.add(
                new FfmpegAudioRenderer(eventHandler, audioRendererEventListener, new DefaultAudioSink.Builder()
                        .setAudioCapabilities(AudioCapabilities.getCapabilities(context))
                        .setEnableFloatOutput(true)
                        .setEnableAudioTrackPlaybackParams(true)
                        .setOffloadMode(DefaultAudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_REQUIRED)
                        .build()
                )
        );

        return renderersList.toArray(new Renderer[0]);
    }
}
