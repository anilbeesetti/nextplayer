# Local FFmpeg performance and rendering verification

Measured 2026-09-06 using Next Player's debug APK with the local nextlib composite build.
Baseline: Next Player `5b5747c7`, nextlib `40a9f16` (including its existing decoder switching).
Fixed nextlib commit: `483ed3c`. The fix retains reference-counted FFmpeg frames in Media3's `decoderPrivate` field until
output is rendered, dropped, flushed or released. Surface rendering converts the original
frame directly into an RGBA native window. The standalone YUV-buffer path converts to
Media3's planar 8-bit 4:2:0 contract, including 10-bit and 4:4:4 inputs.

Other corrections: eliminate the leaked per-packet allocation, zero the decoder's required
input/extradata padding, use global JNI surface references and `IsSameObject`, and release
cached conversion contexts when the decoder is released. Each frame's format and color
metadata determine conversion, so queued frames do not use a newer decoder format.

## Measurement method

- Disposable Pixel 6a profile, ARM64, Android API 37 / Android 37.1, 16 KB pages,
  four virtual CPUs, host GPU. Both APKs use the same FFmpeg 6.0 binaries and Media3 1.11.0.
- Java/Kotlin target 17; the repository's configured Gradle daemon runs on JDK 21.
- `FfmpegPlaybackBenchmarkTest` runs the decoder packaged in Next Player, on a SurfaceView
  hosted in its activity. Encoded samples are preloaded before timing. Four decoder threads
  and four input/output buffers are held constant. Audio is excluded.
- Uncapped decoding measures 600 output frames; rendering measures 300 frames. Each test
  performs one excluded warm-up followed by five measured runs. H.264 is repeated by
  reinstalling the saved baseline and fixed APKs. Medians exclude warm-ups.
- CPU time is process CPU time divided by output frames (summed across threads), not
  wall time or a sampled profiler percentage. Rendering wall time includes the surface's
  approximately 60 Hz pacing. No CPU profiling runs are included in timing results.
- These are emulator/debug measurements, not physical-device or battery-life claims.
  The original surface output is visually wrong, so its speed alone is not a valid success
  criterion. RGBA also changes surface bandwidth; physical-device performance needs checking.
- Native heap snapshots in the raw data include retained FFmpeg frames and exclude some
  Java-managed buffers. They are not comparable total-memory or leak measurements.

## Results

| Workload / metric | Before | After | Change |
| --- | ---: | ---: | ---: |
| H.264 1080p60 uncapped decode throughput | 895.2 fps | 1,087.7 fps | **+21.5%** |
| H.264 uncapped decode CPU / frame | 2.873 ms | 2.725 ms | **−5.2%** |
| H.264 1080p surface decode + render CPU / frame | 6.590 ms | 6.317 ms | **−4.1%** |
| H.264 surface throughput | 60.019 fps | 60.033 fps | Essentially unchanged (60 Hz) |
| HEVC 720p60 10-bit uncapped decode throughput | 1,330.9 fps | 1,397.5 fps | +5.0% |
| HEVC uncapped decode CPU / frame | 1.877 ms | 1.782 ms | −5.1% |
| VP9 720p60 uncapped decode throughput | 814.5 fps | 828.1 fps | +1.7% (small/noisy) |
| VP9 uncapped decode CPU / frame | 1.588 ms | 1.568 ms | −1.3% (small/noisy) |

H.264 values are medians of ten measured runs per revision (two batches of five);
HEVC and VP9 use five measured runs per revision. The strongest repeatable result is
H.264 decode throughput. The first H.264 batch alone measured +20.9% throughput;
the repeat batch measured +18.6%. Small CPU differences and the VP9 result need physical-device
confirmation. These numbers do not imply video plays 21.5% faster: normal playback
keeps the source frame rate and gains decoder headroom.

## Correctness and integration

- Host GPU: baseline has incorrect colors in Next Player and corrupt color-bar PixelCopy
  output. The same pixel assertion fails on the saved baseline and passes after the fix.
- SwiftShader: the original green-output case fails the pixel assertion; the fixed
  APK passes 4:2:0, 10-bit and 4:4:4 color assertions on the same emulator image.
- 8-bit 4:2:0, 10-bit 4:2:0 and 4:4:4 SMPTE bars pass seven RGB patch assertions
  (maximum allowed per-channel error: 12/255). The 4:4:4 fixture also exercises padded
  strides at 854×480. Standalone YUV-buffer output passes its plane/size check.
- Real Next Player flow passes against both revisions: hardware → FFmpeg → hardware →
  FFmpeg → Android software → FFmpeg, seek/pause at 2,000 ms, then resume. Every paused
  switch retained exactly 2,000 ms; the fixed version resumed past 3,000 ms. Its colors
  are corrected. This test uses the actual PlayerActivity, MediaController and PlayerService.
- `assembleDebug`, `test`, `ktlintCheck` and nextlib `:media3ext:test` pass. Unique debug
  test counts: 158 in Next Player and 10 in nextlib. `python3 ffmpeg/test_setup.py` passes.
  Builds cover arm64-v8a, armeabi-v7a, x86 and x86_64; device execution covers ARM64 only.
- The packaged arm64 `libmedia3ext.so` SHA-256 matches the local compiled JNI library:
  `f7ee4b6ccdcb40ac6e383ef774cc1e972cdd62516c526b932391c7833e7c678e`.
  All packaged FFmpeg shared libraries are byte-identical between baseline and fixed APKs.
- HDR tone mapping, physical devices, and audio performance are outside these measurements.

Saved local evidence is in [`build/nextlib-performance`](../../build/nextlib-performance/):
[raw medians](../../build/nextlib-performance/summary.json), per-run JSONL files,
[before](../../build/nextlib-performance/before-playback.png) and
[after](../../build/nextlib-performance/after-playback.png) playback screenshots,
color-bar PNGs, test logs and both APKs. These generated artifacts are ignored by Git.

## Reproduce

Use the opt-in existing composite build; no published dependency version is changed:

```sh
ANDROID_HOME=/path/to/sdk ./gradlew -PnextlibPath=/path/to/nextlib \
  :app:assembleDebug :app:assembleDebugAndroidTest
adb -s SERIAL install -r -g app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
adb -s SERIAL install -r -g app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

Generate a deterministic H.264 workload and the color regression fixtures (host FFmpeg):

```sh
ffmpeg -f lavfi -i testsrc2=size=1920x1080:rate=60 -t 12 \
  -c:v libx264 -preset veryfast -crf 20 -pix_fmt yuv420p -g 60 -bf 2 h264-1080p60.mp4
ffmpeg -f lavfi -i smptebars=size=1280x720:rate=30 -t 4 \
  -c:v libx264 -preset veryfast -crf 16 -pix_fmt yuv420p -g 30 -bf 0 bars-420.mp4
ffmpeg -f lavfi -i testsrc2=size=1280x720:rate=60 -t 12 \
  -c:v libx265 -preset ultrafast -crf 24 -pix_fmt yuv420p10le \
  -x265-params log-level=error:pools=4:keyint=60 hevc-720p60-10bit.mp4
ffmpeg -f lavfi -i testsrc2=size=1280x720:rate=60 -t 12 \
  -c:v libvpx-vp9 -deadline realtime -cpu-used 6 -crf 32 -b:v 0 -g 60 vp9-720p60.webm
# Repeat the bars command with yuv420p10le for bars-10bit.mp4,
# and size=854x480 / yuv444p for bars-444.mp4.
adb -s SERIAL push h264-1080p60.mp4 /sdcard/Movies/h264-1080p60.mp4
adb -s SERIAL push bars-420.mp4 /sdcard/Movies/bars-420.mp4
```

```sh
adb -s SERIAL shell am instrument -w -r \
  -e class io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegPlaybackBenchmarkTest \
  -e clip /sdcard/Movies/h264-1080p60.mp4 -e frames 600 -e runs 5 \
  -e render false -e label before-decode \
  dev.anilbeesetti.nextplayer.debug.test/androidx.test.runner.AndroidJUnitRunner
# Use frames=300 and render=true for the surface benchmark.
# Use a bars clip, frames=60, runs=1, render=true and bars=true for pixel assertions.
# Use render=false / yuv=true to exercise the alternate YUV output contract.
```

The test emits JSONL (including warm-up run 0) and PixelCopy PNGs under
`/sdcard/Android/data/dev.anilbeesetti.nextplayer.debug/files/ffmpeg-benchmark/`.
Use unique labels when repeating a test because JSONL results append.
`am instrument` may exit with shell status zero even when a test fails: require `OK (1 test)`.
The benchmarks skip during ordinary instrumentation runs unless the `clip` argument is supplied.

The real Next Player service/controls regression is separate from the microbenchmark:

```sh
adb -s SERIAL shell am instrument -w -r \
  -e class dev.anilbeesetti.nextplayer.player.LocalDecoderPlaybackTest \
  -e clip /sdcard/Movies/h264-1080p60.mp4 -e label playback \
  dev.anilbeesetti.nextplayer.debug.test/androidx.test.runner.AndroidJUnitRunner
```

It starts playback, selects FFmpeg, pauses/seeks to 2,000 ms, switches through hardware,
FFmpeg, Android software and FFmpeg again, captures the screen and resumes past 3,000 ms.
It requires the initialized decoder category and a paused position within 100 ms of the seek.

To enforce failures in this repository's Gradle tests (the root build otherwise sets
`ignoreFailures = true`), use an init script containing:

```groovy
gradle.projectsEvaluated {
    allprojects { tasks.withType(Test).configureEach { ignoreFailures = false } }
}
```

Run `./gradlew -PnextlibPath=/path/to/nextlib --init-script /path/to/enforce-tests.gradle
assembleDebug test ktlintCheck :nextlib:media3ext:test`.

The surface format follows the [NDK native-window API](https://developer.android.com/ndk/reference/group/a-native-window);
frame ownership uses [FFmpeg AVFrame reference counting](https://ffmpeg.org/doxygen/6.0/structAVFrame.html).
