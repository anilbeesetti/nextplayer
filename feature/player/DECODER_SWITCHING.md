# Runtime decoder switching

NextPlayer uses nextlib's `DecoderManager` on one `ExoPlayer`. Video and audio choices are
independent and stored in each `MediaItem`. New items default to `AUTO`; returning to an
existing playlist item restores its saved choices. Metadata updates preserve those choices.
The video overlay offers HW (`HARDWARE`, all Android system codecs), HW+ (`HW_PLUS`,
FFmpeg extraction with system decoding), and SW (`FFMPEG`). Audio offers HW and SW.
The app owns these modes and maps system decoding to NextLib's `SYSTEM` mode; Android
software MediaCodec implementations are included in HW. The old `SOFTWARE` metadata
value is read as HW.
Automatic selection stays internal; controls show the initialized decoder category and
show “-” while that category is unknown.

```kotlin
val decoderManager = DecoderManager()
val renderersFactory = NextRenderersFactory(context).setDecoderManager(decoderManager)
val player = ExoPlayer.Builder(context).setRenderersFactory(renderersFactory).build()
decoderManager.attach(player)
// Before release: decoderManager.detach()
```

`PlayerService` sends selections to nextlib and reads requested modes from `videoMode` /
`audioMode`. It publishes `activeVideoMode` / `activeAudioMode` and recovery state through
MediaSession extras. `PlayerActivity` receives `onExtrasChanged` and passes the state to
Compose, so a dialog or decoder label can update without a playback-state event. Existing
session extras, such as skip-silence state, are preserved.

`videoDecoderMode` and `audioDecoderMode` in media metadata store requested modes, including
`AUTO`, rather than the active decoder category. The shared selection path updates them for
both user selections and fallback retries. On media transitions the service restores the
saved modes and starts fresh recovery attempts. Choices stay within the current playlist;
they are not saved to the media database.

Adding a local subtitle copies that metadata into a new item, inserts it after the current
item, seeks to it, then removes the old item. This recreates only the current source, while
preserving position, playback intent, and the original shuffle order. `replaceMediaItem`
alone can reuse a source without loading new subtitle configurations. Resume metadata also
uses the current playback position.
Nextlib also restores active modes from successful decoder reuse evaluations: a renderer can be
disabled and enabled again without initializing a new codec.

NextPlayer owns the fallback policy. Video starts with HW in AUTO. Decoder and progressive
parser failures share one retry policy per playlist item. A failed decoder is skipped until
the user selects it again or opens another item.

| Failed video mode | Automatic retry |
| --- | --- |
| HW | HW+ if available, otherwise SW |
| HW+ | HW if it has yet to fail, otherwise SW |
| SW | HW if it has yet to fail |
| No retry left | Player error dialog |

The last working mode takes precedence when it is still available. An early failure after
a manual switch shows the unsupported mode. Acknowledging or dismissing that message starts
the retry, and subsequent attempts run automatically. Video retries below five seconds restart
at zero. Later retries preserve playback position, and all retries preserve playback intent
and playlist order.

Audio uses independent HW/SW recovery. Missing tracks do not trigger fallback. I/O and unrelated
playback errors use the normal player error dialog.

## MX Player Pro 1.93.4 comparison

REA traced the failure handler and the manual-switch dialog in the supplied APK.
The handler removes failed modes from the available set. It also checks MX-specific
codec compatibility before switching between HW and HW+. NextPlayer's hardware retry
uses its Media3/NextLib source and codec capabilities.

- Failure handler `com.mxtech.videoplayer.l.V(int, int)`, REA evidence
  `ev_769daaf763e4c0aedf40ce0493a20f258b666374269e78781fa5ffd6132f3758`
- Manual-switch dialog path `ActivityScreen.H1(byte, byte, boolean)`, REA evidence
  `ev_bf03eb731fe2d3ced18c928fd3763c73e8370fbdcfd808b9bba6c70d539d0702`

On a Pixel 6a emulator running API 37 with 16 KB pages, MX logged HW → HW+ on the
header-prefixed program stream. NextPlayer's CompatibilityServiceTest verified the same
automatic transition, direct SW playback of that fixture, and acknowledgment followed
by a paused restart at zero when the HW+ reader rejects the HDR fixture.

## Local verification

Use the sibling nextlib checkout without changing published dependencies:

```sh
ANDROID_HOME=/path/to/sdk ./gradlew -PnextlibPath=../nextlib assembleDebug test ktlintCheck
```

Without `nextlibPath`, Gradle uses the published version. Tests cover fallback exhaustion,
confirmation, duplicate failures, independent attempts, per-item decoder choices, and session-state
parsing. Device verification must also cover switching while paused/playing, actual decoder
names, position continuity, independent audio/video choices, and fallback dialogs.

## Verification on 2026-09-05

Tested NextPlayer `fa76f296` against local nextlib `72b4125`, including nextlib's merge of
`origin/main` (`aafaf8a`). `assembleDebug`, `test`, and `ktlintCheck` passed with local composite
substitution and test failures enforced (158 NextPlayer tests, 10 nextlib tests). The APK's
arm64 `libmedia3ext.so` SHA-256 matched nextlib's local debug JNI library.

Disposable device: ARM64 Android API 37 / Android 37.1 system image, 16 KB pages, Pixel 6a
profile. Tested SwiftShader and host GPU rendering. No app crash was recorded.

- Automatic H.264 video reported HW (`c2.goldfish.h264.decoder`), AAC audio SW+
  (`c2.android.aac.decoder`). The emulator exposed an initialization-order bug in nextlib;
  `72b4125` fixes it using codec information captured during selection.
- HW → FFmpeg → Android software → HW video switching kept one ExoPlayer instance.
  Paused position stayed within 4 ms (52,947 → 52,951 ms); switching while playing retained
  PLAYING state. Audio choices stayed independent.
- Unsupported HW audio showed confirmation, then recovered to SW+ while retaining video mode.
- MPEG-4 video unsupported by bundled FFmpeg showed confirmation, then recovered to AUTO /
  `c2.android.mpeg4.decoder`. New media reset modes to AUTO.
- Selecting absent audio on video-only media and absent video on audio-only media did not
  trigger false recovery. The audio fixture used the app's private test directory after a
  shared-storage permission error; that source error correctly bypassed decoder fallback.

**Unresolved visual issue:** FFmpeg video output was green with SwiftShader and had incorrect
colors with host GPU rendering. MediaCodec output was correct. The native rendering source is
unchanged from nextlib main; these checks do not establish whether physical devices are affected.
Decoder selection and recovery passed, but FFmpeg visual playback did not.

## HW+ compatibility extraction, 2026-11-16

HW+ and SW start with FFmpeg extraction for clear progressive items. SW can use the
standard reader if the FFmpeg reader rejects the source. Adaptive streams and DRM
use the standard source factory. AUTO retains the current reader and HW selects the
standard reader. A reader change recreates the current item and keeps shuffle order.
Synthetic reloads preserve recovery state.

Live decoder labels come from session extras. Media3 ignores metadata extras when
comparing media items, so a controller's metadata-only timeline snapshot can remain
stale after a decoder selection.

This requires NextLib `1.11.1-0.19.0` containing NextLib PR #237 (`hwplus`, `eeb78cf`).
Normal builds resolve the published artifact; `nextlibPath` is only for local source validation.
It adds libavformat to media3ext. Its own `FFMPEG_EXTRACTION.md` documents the single-reader
I/O contract, supported formats, fatal native I/O errors and HDR limitation (use HW).
No APK/native binaries or personal recording are checked into either repository.

Verified on the ARM64 API 37 / Android 37 emulator, with 16 KB pages:

- Original `ch04_20261110063813.mp4` (307 MB): IMKH prefix followed by MPEG-PS/H.264.
  Standard extraction fails with UnrecognizedInputFormatException; HW+ produces frames
  through c2.goldfish.h264.decoder. Repeated forward/backward seeks and rendered output
  after system -> FFmpeg -> system switching pass.
- Real PlayerService/MediaSession: automatic HW+ fallback, paused seek, live SW label and
  continued playback pass.
- Synthetic H.264/Opus, H.264/Vorbis and cover-art FLAC fixtures produce audio through
  system codecs; the complete FLAC fixture also passes post-read seek and rewind checks.
- Reader tests verify request headers, bounded seek positions and EOF. JVM tests cover
  NAL framing, malformed private data and extraction choice retention.

The user also confirmed playback of the original recording on the physical OPPO.
