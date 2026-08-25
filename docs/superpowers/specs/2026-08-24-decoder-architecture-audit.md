# Graviton decoder architecture audit

Date: 2026-08-24
Branch: `arena/01a034e2-graviton`, base `10dfbbf`

Scope: video decoder selection, codec capability detection, hardware/software fallback and the
diagnostics needed to benchmark them. No player-engine change was made.

---

## A. Current architecture

The pipeline, with the file that implements each stage:

| Stage | Implementation |
| --- | --- |
| App UI / Compose player | `feature/player/src/main/java/com/graviton/feature/player/MediaPlayerScreen.kt` |
| ViewModel / player state | `feature/player/.../PlayerViewModel.kt`, `state/*.kt` |
| Surface host | `feature/player/.../PlayerContentFrame.kt` (`PlayerSurface`, `SURFACE_TYPE_SURFACE_VIEW`) |
| Controller binding | `feature/player/.../PlayerActivity.kt` (`MediaController` per `onStart`) |
| Playback controller / session | `feature/player/.../service/PlayerService.kt` (`MediaSessionService`) |
| ExoPlayer instance | `PlayerService.onCreate()`, `ExoPlayer.Builder(...)` |
| RenderersFactory | `io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory` (external), configured in `PlayerService.onCreate()` |
| Track selection | `androidx.media3.exoplayer.trackselection.DefaultTrackSelector` in `PlayerService.onCreate()` |
| MediaCodec decoder selection | `MediaCodecSelector.DEFAULT` (Media3 default). **Graviton installs no selector of its own.** |
| Decoder mode (HW / HW+ / SW) | `core/model/.../DecoderMode.kt` + `PlayerService.onCreate()` |
| Error handling | `feature/player/.../state/ErrorState.kt` (UI only). **No `onPlayerError` existed in the service.** |
| Fallback | Media3 `setEnableDecoderFallback` only |

Versions (`gradle/libs.versions.toml`): Media3 **1.11.0**; nextlib **1.10.1-0.13.0**, declared at
`feature/player/build.gradle.kts:71-72` (`nextlib-media3ext`, `nextlib-mediainfo`),
`app/build.gradle.kts:144` and `core/data/build.gradle.kts:40`.

### What the renderers factory actually does

`NextRenderersFactory.buildVideoRenderers` calls `super` (which adds one `MediaCodecVideoRenderer`
backed by `MediaCodecSelector.DEFAULT`), then appends its own `FfmpegVideoRenderer`:

```kotlin
super.buildVideoRenderers(...)
if (extensionRendererMode == EXTENSION_RENDERER_MODE_OFF) return
var extensionRendererIndex = out.size            // 1: just MediaCodecVideoRenderer
if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) extensionRendererIndex--   // 0
out.add(extensionRendererIndex++, renderer)
```

So `PREFER` puts FFmpeg at index 0, i.e. **ahead of hardware MediaCodec**, and `ON` puts it behind.
Media3's own docs confirm the ordering is what selects the renderer: "A TrackSelector that prefers
the first suitable renderer will therefore prefer to use an extension renderer to a core renderer."

### The one-off Python scripts

`update_decoder_mode.py`, `update_decoder_selector.py`, `update_decoder_selector_fixed.py`,
`update_playerservice_decoder.py`, `fix_hardware_plus.py`, `fix_player_service.py` and
`fix_recreate.py` were single-shot regex patches left in the repo root. None are referenced by
`.github/workflows/`. Their generated code is the code that shipped — and running them again today
would not compile:

- `update_decoder_mode.py` rewrites `DecoderMode.kt` with three entries, while
  `PlayerService.kt`, `ui/controls/ControlsTopView.kt`, `ui/DecoderSelectorView.kt` and
  `feature/settings/.../extensions/DecoderMode.kt` all still branch on `HARDWARE_PLUS`.
- `update_decoder_selector_fixed.py` and `update_playerservice_decoder.py` delete the
  `HARDWARE_PLUS ->` arm from an exhaustive `when` used as an expression.
- `fix_hardware_plus.py` deletes a `setMediaCodecSelector(...)` block. **This is the one that
  matters**: it removed HW+'s only distinguishing behaviour and left the empty `.apply { }` behind
  in `PlayerService.onCreate()`.
- `fix_recreate.py` patches a `currentPlayer.release()` call that does not exist in the file.

They have been deleted in this change.

---

## B. Current codec support

Evidence for the "HW path" column is `PlayerService.onCreate()`; evidence for the "SW path" column
is nextlib's `FfmpegLibrary.getCodecName()`, which is the complete list of MIME types its FFmpeg
renderer will accept.

| Codec | 8-bit | 10-bit | Current HW path | Current SW path | Problems |
| --- | --- | --- | --- | --- | --- |
| H.264 | Works | Profile-dependent | `MediaCodecVideoRenderer` via `MediaCodecSelector.DEFAULT`, in all four modes | FFmpeg `h264` in AUTO / HW+ / SW (not in HW) | 10-bit is gated on the device advertising `AVCProfileHigh10`; nothing checks that. No fallback in HW/HW+/SW before this change. |
| HEVC | Works | Profile-dependent | same | FFmpeg `hevc` in AUTO / HW+ / SW | Main 10 depends on the device advertising `HEVCProfileMain10`; unchecked. MIME presence is treated as sufficient. |
| AV1 | Device-dependent | **No software path at all** | same | **None** — `FfmpegLibrary.getCodecName()` has no `VIDEO_AV1` case, so `supportsFormat("video/av01")` returns false | No hardware AV1 on older SoCs and no software decoder means a hard failure. Bit depth is unknown to Graviton (AV1 Main covers both 8- and 10-bit). |

Everything marked "device-dependent" is genuinely unknown from the repository: Graviton never reads
a codec capability, so it cannot distinguish a device that supports a profile from one that does
not. There is no hardcoded device list either — the answer simply is not computed.

---

## C. Root causes

1. **No capability inspection at all.** `MediaCodec`, `MediaCodecList`, `MediaCodecSelector`,
   `CodecCapabilities` and `MediaCodecUtil` appear nowhere in the repository. Selection is driven by
   `extensionRendererMode` alone — a MIME-level, renderer-ordering switch. Nothing reads profile,
   level, bit depth, resolution or frame rate.
2. **Decoder fallback was enabled for one mode out of four.**
   `.setEnableDecoderFallback(playerPreferences.decoderMode == DecoderMode.AUTO)` meant HW, HW+ and
   SW turned a recoverable decoder initialisation failure into a hard playback error.
3. **`HARDWARE_PLUS` has no distinct behaviour.** After `fix_hardware_plus.py` stripped the
   `setMediaCodecSelector(...)` call, HW+ mapped to `EXTENSION_RENDERER_MODE_ON` — identical to
   AUTO. Upstream Media3 exposes only three behaviours (`OFF` / `ON` / `PREFER`), so four UI modes
   cannot all be distinct without a custom selector.
4. **Decoder-mode changes do not apply to the running player.** The renderers factory is built once
   in `PlayerService.onCreate()` and nothing observes `decoderMode`. The in-player HW/HW+/SW chip
   only takes effect when the service is next created, i.e. on the next playback session. This is
   the "software decoding exposed as a UI option" failure mode.
5. **No software AV1 decoder exists.** nextlib's FFmpeg build covers H.264, HEVC, MPEG-1/2, VP8 and
   VP9. Its AV1/dav1d support was added and then reverted (nextlib commit `32c521d`,
   "Revert 'Feat: Intial FFmpeg av1 codec support with dav1d'"). This is the single genuine
   capability gap in the stack.
6. **`SOFTWARE` mode bypasses hardware entirely.** `EXTENSION_RENDERER_MODE_PREFER` inserts
   `FfmpegVideoRenderer` at index 0, so selecting SW discards a working hardware decoder. That is
   arguably the point of an explicit "SW" switch, but it is a CPU/battery cost the UI does not warn
   about.
7. **Bit depth is invisible.** nextlib's `VideoStream` exposes only
   `index, title, codecName, language, disposition, bitRate, frameRate, frameWidth, frameHeight,
   rotation` — no profile, no level, no bit depth — and Graviton's `VideoStreamInfo` maps a subset of
   even that.
8. **No error handling in the service.** There was no `onPlayerError`; failures surfaced only as
   `player.playerError` in `ErrorState`. No logging, no retry, no user-facing cause.
9. **nextlib is one Media3 minor behind.** nextlib's newest artifact is `1.10.1-0.13.0`, built
   against Media3 1.10.1; Graviton is on 1.11.0. `NextRenderersFactory` overrides
   `DefaultRenderersFactory.buildVideoRenderers` / `buildAudioRenderers`, both `@UnstableApi`.

---

## D. Recommended architecture

### Implemented in this change

**Capability layer, hardware-first, no device lists.**
`core:model/.../decoder/` holds the pure model and decision:

```
EXACT_HARDWARE -> ALTERNATIVE_HARDWARE -> SOFTWARE -> UNSUPPORTED
```

`DecoderHierarchy.resolve` prefers hardware, and resolves `UNKNOWN` *optimistically towards
hardware*: it is cheaper to let Media3 attempt the hardware decoder and fall back on failure than to
abandon a hardware path that would have worked. Software is only chosen when hardware is
definitively `UNSUPPORTED`.

`feature/player/.../decoder/DeviceDecoderCapabilities.kt` answers the capability question from
`MediaCodecList` at runtime — enumerating decoders, filtering to hardware via
`MediaCodecInfo.isHardwareAccelerated` (API 29+) or codec-name prefix below that, cross-checking
10-bit against advertised `profileLevels`, and validating the resolution/fps/profile/level envelope
with `CodecCapabilities.isFormatSupported`. No Snapdragon/MediaTek/Exynos list.

**Fallback on for every mode.** `DecoderModeConfiguration` is now the single place that maps
`DecoderMode` to the two Media3 knobs, and `enableDecoderFallback` is `true` unconditionally.
Fallback is a retry path, not a preference, so it cannot demote a hardware decoder that initialised
successfully.

**Failure visibility.** `PlaybackDiagnostics` (an `AnalyticsListener`) plus `onPlayerError` log,
under the `GravitonDecoder` tag: selected decoder name, HW / SW(platform) / SW(extension),
initialisation time, MIME, profile, level, bit depth, resolution, frame rate, capability verdict,
chosen path, dropped frames, fallback attempt count, codec errors and playback errors.
`adb logcat -s GravitonDecoder` is the whole benchmark harness for now.

**Dead code removed.** The empty `.apply { }` left by the stripped selector call is gone.

### Recommended next steps, in order

1. **AV1 software decode — the only real gap.** Add dav1d behind the existing Media3 extension
   renderer interface, not libmpv and not a whole new engine. Options, smallest first: Media3's own
   `media3-decoder-av1` (libgav1), or a dav1d-backed `SimpleDecoderVideoRenderer`. Either drops into
   the same `out` list `NextRenderersFactory` already builds, so nothing else changes. Until then
   AV1 on a device without hardware AV1 must report `UNSUPPORTED` rather than fail silently — which
   is what the new capability layer now does.
2. **Give HW+ a real meaning or remove it.** Media3 has three behaviours; Graviton exposes four
   modes. Either restore a custom `MediaCodecSelector` that ranks permissive hardware matches ahead
   of the exact-match list, or collapse HW+ into AUTO and drop it from the UI.
3. **Apply decoder-mode changes to the running player.** This needs a player recreation path
   (rebuild the renderers factory and `ExoPlayer`, reassign `mediaSession.player`, clear the old
   surface before release). It is deliberately **not** in this change: it is the one part that cannot
   be validated without a device, and the repo documents prior damage here — `commit_message.txt`
   records "Fixed a rendering lifecycle race condition during player recreation that could cause
   black screens", and `PlayerContentFrame.kt` pins the SurfaceView choice to the same class of bug.
4. **Warn before software decoding 4K60.** `DecoderHierarchy.isSoftwarePerformanceRisk` already
   computes this; it is logged but not yet surfaced in the UI.
5. **Track nextlib/Media3 together.** Pin or bump nextlib whenever Media3 moves a minor version, and
   verify at runtime that `FfmpegVideoRenderer` was actually added (`Log.i(TAG, "Loaded
   FfmpegVideoRenderer.")`), since a silent signature drift in `buildVideoRenderers` would drop the
   extension renderer with no crash.

---

## E. Files to modify

Already changed:

| File | Why |
| --- | --- |
| `core/model/src/main/java/com/graviton/core/model/decoder/VideoStreamSpec.kt` | New. Codec, MIME, profile, level, bit depth, resolution, fps — the spec a capability check needs. Pure JVM, so it is unit-testable. |
| `core/model/src/main/java/com/graviton/core/model/decoder/DecoderCapability.kt` | New. `HardwareSupport` / `SoftwareSupport` / the combined verdict. |
| `core/model/src/main/java/com/graviton/core/model/decoder/DecoderHierarchy.kt` | New. `DecoderPath` and the hardware-first resolution rule. |
| `core/model/src/test/java/com/graviton/core/model/decoder/DecoderHierarchyTest.kt` | New. Covers the six-cell codec matrix and the UNKNOWN handling. |
| `feature/player/src/main/java/com/graviton/feature/player/decoder/DecoderModeConfiguration.kt` | New. Single source of truth for `DecoderMode` → Media3 knobs; fallback now always on. |
| `feature/player/src/main/java/com/graviton/feature/player/decoder/DeviceDecoderCapabilities.kt` | New. Runtime `MediaCodecList` probe; replaces what would otherwise be a device list. |
| `feature/player/src/main/java/com/graviton/feature/player/decoder/VideoStreamSpecs.kt` | New. Media3 `Format` → `VideoStreamSpec`, including profile → bit depth. |
| `feature/player/src/main/java/com/graviton/feature/player/decoder/PlaybackDiagnostics.kt` | New. The Phase 8 log-only benchmark harness. |
| `feature/player/src/main/java/com/graviton/feature/player/service/PlayerService.kt` | Uses the new configuration, registers the analytics listener, adds `onPlayerError`, drops the dead `.apply { }`. |
| `update_decoder_mode.py`, `update_decoder_selector.py`, `update_decoder_selector_fixed.py`, `update_playerservice_decoder.py`, `fix_hardware_plus.py`, `fix_player_service.py`, `fix_recreate.py` | Deleted. Single-shot regex patches that no longer apply and would not compile if run. |

Still to change for the follow-ups in D:

| File | Why |
| --- | --- |
| `feature/player/build.gradle.kts` | Add the AV1 software decoder dependency (step D1). |
| `feature/player/.../service/PlayerService.kt` | Player recreation so decoder mode applies live (step D3). |
| `core/model/.../DecoderMode.kt`, `feature/player/.../ui/DecoderSelectorView.kt`, `ui/controls/ControlsTopView.kt`, `feature/settings/.../extensions/DecoderMode.kt` | If HW+ is collapsed into AUTO (step D2). |
| `core/ui/src/main/res/values/strings.xml` | Lines 135-140 (`video_software_decoders`, `video_software_decoders_desc`, `prefer_device_decoders`, `prefer_app_decoders`, `device_decoders_only`) and 472 (`hardware_plus`) are unreferenced leftovers — no Kotlin file reads any of them, and the decoder chip uses the literal `"HW+"` instead. Verified by grep. |
| `core/model/.../VideoStreamInfo` and `core/data/.../mappers/StreamInfoMappers.kt` | Add profile/level/bit depth if nextlib's mediainfo ever exposes them. |

---

## F. Dependency changes

**None were added, removed or changed in this change.** Everything new is plain Kotlin against APIs
already on the classpath (`android.media.MediaCodecList`, `androidx.media3.exoplayer.analytics`,
nextlib's `FfmpegLibrary`).

Candidate changes for the follow-ups:

| Change | When |
| --- | --- |
| Add an AV1 software decoder (Media3 `media3-decoder-av1`, or a dav1d-backed renderer) | Step D1. This is the only dependency the audit concludes is genuinely required. |
| Bump `nextlib` in lockstep with Media3 | Step D5. Latest published nextlib is `1.10.1-0.13.0`; there is no 1.11.0 build. |
| **Not** adding libmpv | The audit does not support it. Media3 + MediaCodec + one AV1 decoder covers the matrix; libmpv would add a second playback engine, a second subtitle path and a large native payload for no capability the rest of the stack cannot reach. |

---

## G. Risk assessment

**Devices likely to have hardware limits.** Anything without a hardware AV1 block (broadly pre-2021
SoCs) has no AV1 path today at all, because there is no software AV1 decoder either. HEVC Main 10
hardware is near-universal on SoCs from roughly 2016 on, but H.264 High 10 hardware is rare and
should be expected to fail on most devices. None of this is asserted from a device list — it is what
`DeviceDecoderCapabilities` now reports per device at runtime.

**Software decoding performance.** 10-bit and 4K software decode on a phone CPU will drop frames.
`DecoderHierarchy.isSoftwarePerformanceRisk` flags anything at or above 4K30, or 10-bit at or above
1080p60. `SOFTWARE` mode currently routes *everything* to FFmpeg including streams the hardware
could handle, so it is the highest-risk user-selectable setting.

**Battery and thermal.** Sustained software decode is a continuous full-CPU load and will throttle.
Hardware-first resolution plus the always-on fallback keeps the common case on hardware.

**APK size.** Unchanged in this change. An AV1 software decoder adds roughly 1-3 MB per ABI
compressed; shipping only `arm64-v8a` and `armeabi-v7a` keeps that bounded. libmpv would be an order
of magnitude larger, which is part of why it is not recommended.

**ABI and native libraries.** nextlib already ships `libmedia3ext.so`. An AV1 decoder must match its
ABI set and needs 16 KB page-size alignment on Android 15+ (nextlib added this in `e684dff`).

**Android version compatibility.** `minSdk` is 23, so `MediaCodecList`, `getCapabilitiesForType`,
`profileLevels` and `isFormatSupported` (all API 21) are always available.
`MediaCodecInfo.isHardwareAccelerated` is API 29 and is guarded by an `SDK_INT` check, with a
codec-name-prefix fallback below it. `CodecCapabilities.isFormatSupported` is documented as a hint
and some devices answer it wrongly; that is why `HardwareSupport.UNKNOWN` exists and why fallback
stays on — a wrong "no" is recoverable.

**Unchecked items.** Three things could not be verified in this environment and should be confirmed
on a device or a full build:

1. Whether Media3 1.11.0's `DefaultRenderersFactory.buildVideoRenderers` signature is still
   binary-compatible with nextlib compiled against 1.10.1. If it is not, the override silently stops
   applying and the FFmpeg renderers are never added — no crash, just no software decode. The
   `"Loaded FfmpegVideoRenderer."` log line is the check.
2. Whether Media3's `Format.profile` uses `MediaCodecInfo.CodecProfileLevel` numbering. `VideoStreamSpecs.kt`
   assumes it does; the diagnostics log prints the raw profile so a mismatch is visible.
3. All runtime behaviour. Nothing in this change was executed on a device.

---

## Verification

`./gradlew` cannot run in this sandbox. The specific command that should have validated the new
pure-JVM logic fails before doing any work:

```
$ JAVA_HOME=<jre> ./gradlew :core:model:test
Fetching distribution.
Downloading https://services.gradle.org/distributions/gradle-9.7.0-bin.zip

Attempt 1/1 failed. Reason: Remote host terminated the handshake
Exception in thread "main" javax.net.ssl.SSLHandshakeException: Remote host terminated the handshake
    at java.base/sun.security.ssl.SSLSocketImpl.handleEOF(Unknown Source)
    ...
```

`./gradlew --version` fails the same way, with the download traced through
`org.gradle.wrapper.Download.download` / `org.gradle.wrapper.Install.createDist`.

`gradle/wrapper/gradle-wrapper.properties` points at
`https://services.gradle.org/distributions/gradle-9.7.0-bin.zip`, and this sandbox blocks
`services.gradle.org`, `repo1.maven.org`, `dl.google.com` and `api.adoptium.net` at the TCP layer
(DNS resolves; the handshake is reset). `pypi.org` and `registry.npmjs.org` are reachable, which is
how a JRE was obtained to run the wrapper at all — but there is no `javac`, no Android SDK and no
route to the AndroidX artifacts, so `assembleDebug`, `test` and `ktlintCheck` are all unavailable
here. **No test or build was executed against the new code.**

What was checked instead, and is stated as such:

- Every external symbol the new code references was confirmed against its source or the official
  reference: `NextRenderersFactory` and `FfmpegLibrary` (nextlib, fetched from GitHub);
  `AnalyticsListener.onVideoDecoderInitialized` / `onVideoInputFormatChanged` /
  `onVideoDecoderReleased` / `onDroppedVideoFrames` / `onVideoCodecError` and the
  `MediaCodecInfo.CodecProfileLevel` profile constants (Android reference).
- `MediaCodecInfo.CodecProfileLevel` constants added after API 23 (`AV1ProfileMain10`,
  `HEVCProfileMain10HDR10Plus`, ...) are compile-time constants, so they inline and raise an
  `InlinedApi` warning rather than a `NewApi` error. `isHardwareAccelerated` (API 29) is the only
  non-constant API-29 call and is behind an `SDK_INT` check.
- Brace/paren balance and import ordering (ktlint `android_studio`) were checked mechanically for
  every touched file, and the two imports removed from `PlayerService.kt`
  (`DefaultRenderersFactory`, `DecoderMode`) have no remaining references.
