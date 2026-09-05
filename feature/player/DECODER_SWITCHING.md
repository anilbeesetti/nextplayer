# Runtime decoder switching

NextPlayer uses nextlib's `DecoderManager` on one `ExoPlayer`. Video and audio choices are
independent and reset to `AUTO` for a new playlist item, but survive metadata updates.
The overlay offers HW (`HARDWARE`), SW+ (`SOFTWARE` MediaCodec), and SW (`FFMPEG`).
Automatic selection stays internal; controls show the initialized decoder category and
show “Decoders” while that category is unknown.

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

NextPlayer owns fallback policy. Each track keeps a bounded queue of fallback modes;
duplicate failures for the same attempt are ignored.

| Failed selection | Next attempts |
| --- | --- |
| `AUTO` | Silently try `FFMPEG` |
| Explicit `HARDWARE` | Confirm, then `SOFTWARE`, then `FFMPEG` |
| Explicit `SOFTWARE` | Confirm, then `FFMPEG` |
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
confirmation, duplicate failures, independent attempts, media identity, and session-state
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
