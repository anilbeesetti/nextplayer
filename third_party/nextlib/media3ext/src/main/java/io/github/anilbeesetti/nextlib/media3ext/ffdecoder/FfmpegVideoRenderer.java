package io.github.anilbeesetti.nextlib.media3ext.ffdecoder;

import static java.lang.Runtime.getRuntime;

import android.os.Handler;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.TraceUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.CryptoConfig;
import androidx.media3.decoder.Decoder;
import androidx.media3.decoder.DecoderException;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.VideoDecoderOutputBuffer;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.video.DecoderVideoRenderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;


@UnstableApi
public final class FfmpegVideoRenderer extends DecoderVideoRenderer {

    private static final String TAG = "FfmpegVideoRenderer";

    private static final int DEFAULT_NUM_OF_INPUT_BUFFERS = 4;
    private static final int DEFAULT_NUM_OF_OUTPUT_BUFFERS = 4;
    /* Default size based on 720p resolution video compressed by a factor of two. */
    private static final int DEFAULT_INPUT_BUFFER_SIZE =
            Util.ceilDivide(1280, 64) * Util.ceilDivide(720, 64) * (64 * 64 * 3 / 2) / 2;

    /** The number of input buffers. */
    private final int numInputBuffers;
    /**
     * The number of output buffers. The renderer may limit the minimum possible value due to
     * requiring multiple output buffers to be dequeued at a time for it to make progress.
     */
    private final int numOutputBuffers;

    private final int threads;

    @Nullable private FfmpegVideoDecoder decoder;

    /**
     * Creates a new instance.
     *
     * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
     *     can attempt to seamlessly join an ongoing playback.
     * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
     *     null if delivery of events is not required.
     * @param eventListener A listener of events. May be null if delivery of events is not required.
     * @param maxDroppedFramesToNotify The maximum number of frames that can be dropped between
     *     invocations of {@link VideoRendererEventListener#onDroppedFrames(int, long)}.
     */
    public FfmpegVideoRenderer(
            long allowedJoiningTimeMs,
            @Nullable Handler eventHandler,
            @Nullable VideoRendererEventListener eventListener,
            int maxDroppedFramesToNotify) {
        this(
                allowedJoiningTimeMs,
                eventHandler,
                eventListener,
                maxDroppedFramesToNotify,
                /* threads= */ getRuntime().availableProcessors(),
                DEFAULT_NUM_OF_INPUT_BUFFERS,
                DEFAULT_NUM_OF_OUTPUT_BUFFERS);
    }

    /**
     * Creates a new instance.
     *
     * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
     *     can attempt to seamlessly join an ongoing playback.
     * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
     *     null if delivery of events is not required.
     * @param eventListener A listener of events. May be null if delivery of events is not required.
     * @param maxDroppedFramesToNotify The maximum number of frames that can be dropped between
     *     invocations of {@link VideoRendererEventListener#onDroppedFrames(int, long)}.
     * @param threads Number of threads libgav1 will use to decode.
     * @param numInputBuffers Number of input buffers.
     * @param numOutputBuffers Number of output buffers.
     */
    public FfmpegVideoRenderer(
            long allowedJoiningTimeMs,
            @Nullable Handler eventHandler,
            @Nullable VideoRendererEventListener eventListener,
            int maxDroppedFramesToNotify,
            int threads,
            int numInputBuffers,
            int numOutputBuffers) {
        super(allowedJoiningTimeMs, eventHandler, eventListener, maxDroppedFramesToNotify);
        this.threads = threads;
        this.numInputBuffers = numInputBuffers;
        this.numOutputBuffers = numOutputBuffers;
    }

    @Override
    public String getName() {
        return TAG;
    }

    @Override
    @RendererCapabilities.Capabilities
    public final int supportsFormat(Format format) {
        String mimeType = Assertions.checkNotNull(format.sampleMimeType);
        if (!FfmpegLibrary.isAvailable() || !MimeTypes.isVideo(mimeType)) {
            return C.FORMAT_UNSUPPORTED_TYPE;
        } else if (!FfmpegLibrary.supportsFormat(format.sampleMimeType)) {
            return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE);
        } else if (format.drmInitData != null) {
            return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_DRM);
        } else {
            return RendererCapabilities.create(
                    C.FORMAT_HANDLED,
                    ADAPTIVE_SEAMLESS,
                    TUNNELING_NOT_SUPPORTED);
        }
    }


    @Override
    protected void renderOutputBufferToSurface(VideoDecoderOutputBuffer outputBuffer, Surface surface)
            throws FfmpegDecoderException {
        if (decoder == null) {
            throw new FfmpegDecoderException(
                    "Failed to render output buffer to surface: decoder is not initialized.");
        }
        decoder.renderToSurface(outputBuffer, surface);
        outputBuffer.release();
    }

    @Override
    protected Decoder<DecoderInputBuffer, ? extends VideoDecoderOutputBuffer, ? extends DecoderException> createDecoder(Format format, @Nullable CryptoConfig cryptoConfig) throws DecoderException {
        TraceUtil.beginSection("createFfmpegVideoDecoder");
        int initialInputBufferSize = format.maxInputSize != Format.NO_VALUE ? format.maxInputSize : DEFAULT_INPUT_BUFFER_SIZE;
        FfmpegVideoDecoder decoder = new FfmpegVideoDecoder(numInputBuffers, numOutputBuffers, initialInputBufferSize, threads, format);
        this.decoder = decoder;
        TraceUtil.endSection();
        return decoder;
    }

    @Override
    protected void setDecoderOutputMode(@C.VideoOutputMode int outputMode) {
        if (decoder != null) {
            decoder.setOutputMode(outputMode);
        }
    }

}
