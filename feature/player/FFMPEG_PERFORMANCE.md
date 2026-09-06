# Local FFmpeg performance and rendering verification

Measured 2026-09-06 using Next Player's debug APK with the local nextlib composite build.
Baseline: Next Player `5b5747c7`, nextlib `40a9f16` (including its existing decoder switching).
Initial nextlib performance revision: `483ed3c`; review corrections are recorded below. The fix retains reference-counted FFmpeg frames in Media3's `decoderPrivate` field until
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

## Initial results (`483ed3c`)

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

Reviewable evidence is committed in [`verification/ffmpeg`](verification/ffmpeg/):
[raw runs](verification/ffmpeg/runs.jsonl), [medians](verification/ffmpeg/summary.json),
[before](verification/ffmpeg/before-playback.png) and
[after](verification/ffmpeg/after-playback.png) Next Player screenshots, and the
[green SwiftShader output](verification/ffmpeg/before-swiftshader-bars-420.png) with its
[corrected color bars](verification/ffmpeg/after-swiftshader-bars-420.png).
The raw runs include excluded warm-ups (`run = 0`). Additional test logs and both APKs
remain in the local, Git-ignored `build/nextlib-performance/` directory.

## Review corrections and repeat measurements

The six review observations were confirmed against FFmpeg 6.0, Media3 1.11.0 and Android's
Surface implementation. The corrections are in nextlib `06d62f0`, with a surface-disconnect retry follow-up in `a884d8d`:

- Preserve Media3 `COLORSPACE_UNKNOWN` for unspecified matrices and use its BT.709 fallback
  for RGBA conversion. Explicit BT.601 and BT.2020 metadata retain their corresponding matrix.
- Configure range before `sws_init_context`, so full-range planar YUV takes the range conversion
  path. Recreate the cached scaler when size, format, matrix or range changes.
- Disconnect invalid locked buffers before unlocking, preventing the unwritten image from
  being queued. Release the window so it can be reacquired. This also handles failed scaling.
  Android has no public unlock-without-post operation; its
  [Surface implementation](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/main/libs/gui/Surface.cpp)
  clears slots on disconnect, then rejects posting the removed slot while still unlocking it.
- Retain the complete set of allocated output buffers. After joining the decode thread, free
  every native frame, including outputs held by the renderer, and clear their native pointers.
  A late output release is safe. `flush()` alone cannot reclaim renderer-held outputs.
- Use the existing `FfmpegLibrary.getInputBufferPaddingSize()` JNI getter instead of duplicating 64.

The pre-review APK fails both the renderer-held-frame and untagged BT.709 pixel regressions.
The native range check also fails with the pre-review scaler. See the
[before/after regression logs](verification/ffmpeg/review/).

The corrected APK passes ten instrumentation invocations on the same disposable ARM64
API 37 / Android 37.1 emulator with the host GPU: held-frame cleanup (including late release),
five color-bar fixtures, unknown/BT.601 YUV metadata, full-to-limited YUV values, and actual
Next Player decoder switching/pause/seek/resume. The native check uses the bundled FFmpeg to
verify matrix/range cache transitions and injects null bits, wrong format, undersized width,
undersized height and invalid stride into real locked Android buffers. All five error cases
publish zero images, and each same Surface successfully renders a subsequent frame.

Final verification of `a884d8d`: `assembleDebug`, `:app:assembleDebugAndroidTest`, `test`,
`ktlintCheck`, nextlib `:media3ext:test`, and `python3 ffmpeg/test_setup.py` pass. The native
regressions and actual Next Player switching flow were rerun successfully. The final packaged
ARM64 JNI library matches the local build, SHA-256
`d19bc3e18421adbeef22f5e80416432a282d5b806307dbe25c82db717c048c79`.
All five packaged FFmpeg libraries remain byte-identical to the original baseline. The final
APK was installed and launched on the connected CPH2689 Android 16 phone for manual testing;
no phone performance results are claimed.

The original SMPTE fixtures were generated with BT.601 coefficients without tagging the
matrix. The reproduction command now tags them explicitly. A separate HD fixture converts
the bars to BT.709 and strips the matrix metadata; the expected RGB tolerance stays 12/255.
This makes the fallback test distinct from the explicit-matrix tests.

A fresh H.264 comparison uses five measured runs per revision plus one excluded warm-up,
600 frames for uncapped decode and 300 for rendering. Baseline is still `40a9f16`; corrected
revision is `06d62f0`. Other settings match the original method. These results are a separate
batch, not pooled with the original ten-run comparison:

| H.264 1080p60 metric | Baseline | Review fix | Change |
| --- | ---: | ---: | ---: |
| Uncapped decode | 879.19 fps | 1,027.26 fps | +16.8% |
| Decode CPU/frame | 2.880 ms | 2.730 ms | −5.2% |
| Decode + render CPU/frame | 6.257 ms | 6.377 ms | +1.9% |
| Surface throughput | 60.031 fps | 60.033 fps | Essentially unchanged |

Decode headroom remains improved. This batch does not establish a rendering CPU saving;
the small difference reverses the initial measurement. HEVC/VP9 were not remeasured for the
review corrections. [Raw repeat runs](verification/ffmpeg/review/runs.jsonl) and
[medians](verification/ffmpeg/review/summary.json) include warm-up exclusion explicitly.

Run the native regression check from nextlib on a disposable ARM64 API 26+ target:

```sh
ANDROID_NDK_HOME=/path/to/ndk ANDROID_SERIAL=SERIAL \
  media3ext/src/test/cpp/run_ffvideo_test.sh
```

Additional fixture commands:

```sh
# BT.709 values with deliberately absent matrix metadata.
ffmpeg -f lavfi -i smptebars=size=1280x720:rate=30 -t 4 \
  -vf scale=in_color_matrix=bt601:out_color_matrix=bt709 \
  -c:v libx264 -preset veryfast -crf 16 -pix_fmt yuv420p -g 30 -bf 0 \
  -colorspace unknown -x264-params colormatrix=undef bars-709-untagged.mp4
# Full-range black/white halves; output Y must become 16/235.
ffmpeg -f lavfi \
  -i "nullsrc=s=1280x720:r=30,geq=lum='if(lt(X,W/2),0,255)':cb=128:cr=128" \
  -t 4 -c:v libx264 -preset veryfast -qp 0 -pix_fmt yuv420p \
  -colorspace bt709 -color_range pc -g 30 -bf 0 range-full.mp4
```

Use the instrumentation command below with `render=false, hold=true` for release cleanup;
`render=false, yuv=true, colorspace=0` for the untagged clip; and
`render=false, yuv=true, range=true, colorspace=2` for the black/white clip. Pass these
as separate `-e` arguments. With `bars=true`, all seven RGB patches must pass unchanged.
The native test additionally covers full-range `YUV420P` without the deprecated `YUVJ420P`
pixel format, which is the case that exposed the unscaled-copy bug.

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
  -c:v libx264 -preset veryfast -crf 16 -pix_fmt yuv420p -colorspace smpte170m -g 30 -bf 0 bars-420.mp4
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
