package dev.anilbeesetti.libs.ffcodecs;

import android.content.Context;
import android.os.Handler;

import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;

import java.lang.reflect.Constructor;
import java.util.ArrayList;

@UnstableApi
public class FfmpegRenderersFactory extends DefaultRenderersFactory {

    /**
     * @param context A {@link Context}.
     */
    public FfmpegRenderersFactory(Context context) {
        super(context);
    }
    
    private static final String TAG = "FFmpegRenderersFactory";

    @Override
    protected void buildAudioRenderers(Context context, int extensionRendererMode, MediaCodecSelector mediaCodecSelector, boolean enableDecoderFallback, AudioSink audioSink, Handler eventHandler, AudioRendererEventListener eventListener, ArrayList<Renderer> out) {
        super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback, audioSink, eventHandler, eventListener, out);

        if (extensionRendererMode == EXTENSION_RENDERER_MODE_OFF) {
            return;
        }

        try {
            // Full class names used for constructor args so the LINT rule triggers if any of them move.
            Class<?> clazz = Class.forName("dev.anilbeesetti.libs.ffcodecs.FfmpegAudioRenderer");
            Constructor<?> constructor =
                    clazz.getConstructor(
                            android.os.Handler.class,
                            androidx.media3.exoplayer.audio.AudioRendererEventListener.class,
                            androidx.media3.exoplayer.audio.AudioSink.class);
            Renderer renderer =
                    (Renderer) constructor.newInstance(eventHandler, eventListener, audioSink);
            out.add(renderer);
            Log.i(TAG, "Loaded FfmpegAudioRenderer.");
        } catch (ClassNotFoundException e) {
            // Expected if the app was built without the extension.
        } catch (Exception e) {
            // The extension is present, but instantiation failed.
            throw new RuntimeException("Error instantiating FFmpeg extension", e);
        }
    }
}
