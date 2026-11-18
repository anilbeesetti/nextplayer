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

NextPlayer owns fallback policy. Each track keeps a bounded queue of fallback modes;
duplicate failures for the same attempt are ignored.

| Failed selection | Next attempts |
| --- | --- |
| `AUTO` | Silently try `FFMPEG` |
| Explicit `HARDWARE` | Confirm, then `FFMPEG` |
| Explicit `HW_PLUS` | Confirm, then `FFMPEG` |
| Explicit `FFMPEG` | Confirm, then `AUTO` |
| Exhausted queue | Show the player error dialog |

Only present, unsupported tracks or decoder-related playback errors start recovery.
Missing audio/video tracks do not. Decoder initialization clears recovery for that track.
Non-decoder errors use the normal error dialog. A retry prepares the player if it currently
has an error; otherwise nextlib handles track remapping and codec restarts. Position,
playlist, and `playWhenReady` remain intact.

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

`ApiMediaSourceFactory` uses NextLib's `FfmpegMediaSourceFactory` for clear progressive
items marked for FFmpeg extraction. Adaptive streams and DRM keep the standard source
factory. Unrecognized progressive input in AUTO tries HW+ once; explicit SW can likewise
retry extraction without changing its decoder. The extraction flag is separate from the
requested decoder and survives switching from HW+ to SW or AUTO. Selecting HW restores the
standard extractor. A source change recreates only the current item, preserving paused
position, playback intent and shuffle order. Synthetic same-item reloads do not reset
recovery queues.

Live decoder labels come from session extras. Media3 ignores metadata extras when
comparing media items, so a controller's metadata-only timeline snapshot can remain
stale after a decoder selection. Do not use that snapshot as live decoder state.

Decoder failures in HW retry SW directly: HW and HW+ share the same system decoders.
If explicit HW+ extraction rejects a container (including HDR), retry the standard HW
reader once, retaining position and pause state. Explicit HW does not automatically
retry HW+, preventing a reader-recovery loop. Adaptive/DRM source selection remains
unchanged when a requested mode only changes the extraction flag.

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
