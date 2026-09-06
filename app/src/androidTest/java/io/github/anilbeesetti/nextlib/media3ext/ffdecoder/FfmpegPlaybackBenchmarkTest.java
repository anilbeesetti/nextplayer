package io.github.anilbeesetti.nextlib.media3ext.ffdecoder;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.SurfaceView;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.decoder.Decoder;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.VideoDecoderOutputBuffer;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Opt-in benchmark against the native decoder packaged in the Next Player APK. */
@RunWith(AndroidJUnit4.class)
public class FfmpegPlaybackBenchmarkTest {
    @Test
    public void decodeAndRender() throws Exception {
        Bundle args = InstrumentationRegistry.getArguments();
        String clip = args.getString("clip");
        org.junit.Assume.assumeTrue("Pass -e clip /path/to/video", clip != null);
        boolean render = Boolean.parseBoolean(args.getString("render", "true"));
        boolean yuv = Boolean.parseBoolean(args.getString("yuv", "false"));
        int runs = Integer.parseInt(args.getString("runs", "3"));
        int maxFrames = Integer.parseInt(args.getString("frames", "300"));
        String label = args.getString("label", "run");
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var context = instrumentation.getTargetContext();
        File outputDir = new File(context.getExternalFilesDir(null), "ffmpeg-benchmark");
        assertTrue(outputDir.isDirectory() || outputDir.mkdirs());
        List<byte[]> samples = new ArrayList<>();
        List<Long> timestamps = new ArrayList<>();
        Format format;
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(clip);
            MediaFormat mediaFormat = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                    extractor.selectTrack(i);
                    mediaFormat = extractor.getTrackFormat(i);
                    break;
                }
            }
            assertNotNull("No video track", mediaFormat);
            List<byte[]> initializationData = new ArrayList<>();
            for (int i = 0; mediaFormat.containsKey("csd-" + i); i++) {
                ByteBuffer csd = mediaFormat.getByteBuffer("csd-" + i);
                byte[] data = new byte[csd.remaining()];
                csd.get(data);
                initializationData.add(data);
            }
            format = new Format.Builder()
                    .setSampleMimeType(mediaFormat.getString(MediaFormat.KEY_MIME))
                    .setWidth(mediaFormat.getInteger(MediaFormat.KEY_WIDTH))
                    .setHeight(mediaFormat.getInteger(MediaFormat.KEY_HEIGHT))
                    .setInitializationData(initializationData).build();
            ByteBuffer data = ByteBuffer.allocateDirect(8 * 1024 * 1024);
            while (samples.size() < maxFrames + 40) {
                data.clear();
                int size = extractor.readSampleData(data, 0);
                if (size < 0) break;
                byte[] sample = new byte[size];
                data.position(0);
                data.get(sample);
                samples.add(sample);
                timestamps.add(extractor.getSampleTime());
                extractor.advance();
            }
        } finally {
            extractor.release();
        }
        final SurfaceView[] view = new SurfaceView[1];
        try (ActivityScenario<PlayerActivity> activity = ActivityScenario.launch(
                new Intent(context, PlayerActivity.class))) {
            activity.onActivity(a -> {
                view[0] = new SurfaceView(a);
                a.setContentView(view[0]);
            });
            long deadline = SystemClock.elapsedRealtime() + 10000;
            while (!view[0].getHolder().getSurface().isValid() && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(20);
            }
            assertTrue("No surface", view[0].getHolder().getSurface().isValid());
            // Run zero warms native code and surface allocation; exclude it from comparisons.
            for (int run = 0; run <= runs; run++) {
                FfmpegVideoDecoder decoder = new FfmpegVideoDecoder(4, 4, 1024 * 1024, 4, format);
                decoder.setOutputMode(yuv ? C.VIDEO_OUTPUT_MODE_YUV : C.VIDEO_OUTPUT_MODE_SURFACE_YUV);
                Semaphore available = new Semaphore(0);
                decoder.setCallback(new Decoder.Callback() {
                    @Override public void onInputBufferAvailable() { available.release(); }
                    @Override public void onOutputBufferAvailable() { available.release(); }
                }, Runnable::run);
                int sent = 0;
                int frames = 0;
                long renderNs = 0;
                long start = SystemClock.elapsedRealtimeNanos();
                long cpuStart = Process.getElapsedCpuTime();
                long heapStart = Debug.getNativeHeapAllocatedSize();
                try {
                    while (frames < maxFrames && SystemClock.elapsedRealtimeNanos() - start < 60000000000L) {
                        DecoderInputBuffer input;
                        while (sent < samples.size() && (input = decoder.dequeueInputBuffer()) != null) {
                            input.ensureSpaceForWrite(samples.get(sent).length);
                            input.data.put(samples.get(sent));
                            input.timeUs = timestamps.get(sent);
                            input.format = format;
                            input.flip();
                            decoder.queueInputBuffer(input);
                            sent++;
                        }
                        VideoDecoderOutputBuffer output;
                        boolean received = false;
                        while (frames < maxFrames && (output = decoder.dequeueOutputBuffer()) != null) {
                            received = true;
                            try {
                                if (render) {
                                    long renderStart = SystemClock.elapsedRealtimeNanos();
                                    decoder.renderToSurface(output, view[0].getHolder().getSurface());
                                    renderNs += SystemClock.elapsedRealtimeNanos() - renderStart;
                                } else if (yuv) {
                                    assertNotNull(output.yuvPlanes);
                                    assertTrue(output.yuvPlanes[0].remaining() >= format.width * format.height);
                                }
                                frames++;
                            } finally {
                                output.release();
                            }
                        }
                        if (!received && !available.tryAcquire(2, TimeUnit.SECONDS)) break;
                    }
                    long elapsedNs = SystemClock.elapsedRealtimeNanos() - start;
                    long cpuMs = Process.getElapsedCpuTime() - cpuStart;
                    JSONObject result = new JSONObject()
                            .put("label", label).put("clip", new File(clip).getName())
                            .put("render", render).put("yuv", yuv).put("run", run)
                            .put("frames", frames).put("samples", sent)
                            .put("elapsedMs", elapsedNs / 1000000.0)
                            .put("fps", frames * 1e9 / elapsedNs)
                            .put("cpuMs", cpuMs).put("cpuMsPerFrame", (double) cpuMs / frames)
                            .put("renderMsPerFrame", renderNs / 1e6 / frames)
                            .put("nativeHeapDeltaBytes", Debug.getNativeHeapAllocatedSize() - heapStart);
                    android.util.Log.i("FfmpegBenchmark", result.toString());
                    try (FileOutputStream out = new FileOutputStream(new File(outputDir, label + ".jsonl"), true)) {
                        out.write((result + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                    assertEquals("Decoded frame count", maxFrames, frames);
                    if (render && run == runs) {
                        Bitmap bitmap = Bitmap.createBitmap(format.width, format.height, Bitmap.Config.ARGB_8888);
                        CountDownLatch copied = new CountDownLatch(1);
                        int[] copyResult = {-1};
                        PixelCopy.request(view[0], bitmap, code -> {
                            copyResult[0] = code;
                            copied.countDown();
                        }, new Handler(Looper.getMainLooper()));
                        assertTrue(copied.await(5, TimeUnit.SECONDS));
                        assertEquals("PixelCopy", PixelCopy.SUCCESS, copyResult[0]);
                        try (FileOutputStream out = new FileOutputStream(new File(outputDir, label + ".png"))) {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                        }
                        if (Boolean.parseBoolean(args.getString("bars", "false"))) {
                            // SMPTE 75% bars, sampled away from chroma boundaries.
                            int[][] expected = {{191,191,191}, {191,191,0}, {0,191,191},
                                    {0,191,0}, {191,0,191}, {191,0,0}, {0,0,191}};
                            for (int bar = 0; bar < expected.length; bar++) {
                                int pixel = bitmap.getPixel((2 * bar + 1) * bitmap.getWidth() / 14,
                                        bitmap.getHeight() / 3);
                                int[] actual = {Color.red(pixel), Color.green(pixel), Color.blue(pixel)};
                                for (int channel = 0; channel < 3; channel++) {
                                    assertTrue("Incorrect color in bar " + bar + ": " + pixel,
                                            Math.abs(actual[channel] - expected[bar][channel]) <= 12);
                                }
                            }
                        }
                        bitmap.recycle();
                    }
                } finally {
                    decoder.release();
                }
            }
        }
    }
}
