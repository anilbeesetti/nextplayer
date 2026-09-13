# Runtime decoder switching

NextPlayer uses nextlib's `DecoderManager` on one `ExoPlayer`. Video and audio choices are
independent and reset to `AUTO` for a new playlist item, but survive metadata updates.
The overlay offers HW (`HARDWARE`), SW+ (`SOFTWARE` MediaCodec), and SW (`FFMPEG`).
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

Adding a local subtitle rebuilds the media sources at the same playlist index and position,
preserving shuffle order. `replaceMediaItem` alone can reuse the old source without loading
new subtitle configurations. The reload must not temporarily seek to a new index, which would
reset manual decoder choices. Its resume metadata must also use the current playback position.
Nextlib also restores active modes from successful decoder reuse evaluations: a renderer can be
disabled and enabled again without initializing a new codec.

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


## Verification on 2026-09-12

Tested NextPlayer `5f6aef14` with local nextlib `23417ad`. The app changes also passed
`assembleDebug test ktlintCheck` against published nextlib `1.11.0-0.15.0`. The complete
fix passed those checks with composite substitution, 206 NextPlayer JVM tests, 11 nextlib
JVM tests, and the new nextlib decoder lifecycle instrumentation regression. Test failures
were enforced with a temporary Gradle init script overriding the root `ignoreFailures = true`.
Builds used the checked-in Gradle daemon configuration and Java 17 bytecode target.

Disposable device: Pixel 6a profile, Android 17 / API 37 (`android-37.1` system image),
ARM64, 16 KB pages, 720 × 1600. Final checks used host graphics.

- Reproduced HW → “-” after adding a local SRT on the original app. Both new regressions
  failed against the original implementations and passed with the fixes.
- All six directed transitions among HW, SW+, and SW passed while paused and again while
  playing. Paused position stayed exactly 30,255 ms; playing position advanced throughout.
  Independently selected SW audio survived every video transition.
- Local subtitles preserved all three manual video modes and the paused position
  (163,649 ms). Starting subtitle selection while playing retained HW and resumed playback.
- Rapid repeated mode selections settled on the final requested HW decoder.
- Unsupported HW audio prompted and recovered to SW+ while retaining SW video; repeating
  the failure recovered again. Unsupported FFmpeg MPEG-4 video prompted and recovered to SW+.
- New media reset automatic selection. Video-only and audio-only media tolerated all three
  choices for the absent track without false recovery; the present track remained selectable.
  Audio-only testing used an app-private fixture because the app requests video storage access.
  The inaccessible shared audio fixture correctly used the ordinary source-error dialog.
- FFmpeg colors and subtitles rendered correctly on the H.264 fixture. No app crash was recorded.

The first SwiftShader run was interrupted by a host emulator SIGABRT in gRPC
`CallbackWithSuccessTag`. The complete transition matrix passed after restarting with host
graphics. The disposable device was shut down and its data removed after verification.

The general decoder-reuse fix is in nextlib `23417ad`, published in `1.11.1-0.16.0`.
NextPlayer now uses that release; local composite substitution is no longer required.

## Local subtitle regression verification on 2026-09-13

The September 12 checks did not establish immediate availability of a newly added subtitle
track. The metadata-only replacement introduced in `5f6aef14` preserved the codec label but
could leave the subtitle missing until reopening. This was reproduced on `71c065f9`.

With nextlib `1.11.1-0.16.0`, the corrected reload passed `assembleDebug test ktlintCheck`
with test failures enforced. The expanded `PlayerTest` fails on the previous implementation
and verifies source recreation, playlist identity/order, shuffle order, resume metadata,
position, playback intent, and duplicate additions. `LocalSubtitleTest` also fails before
the fix and passes afterward: real playback discovers first and subsequent SRT tracks and
renders their cues without reopening, while paused and playing.

Disposable Pixel 6a profile: Android 17 / API 37, ARM64, 16 KB pages, 720 × 1600, host graphics.
The app's Subtitle → Open local subtitle flow was verified with generated H.264/AAC video
and three distinct SRT files stored separately in Downloads to prevent automatic discovery.

- First subtitle appeared immediately in the list, was selected, and rendered with manual HW.
- A second subtitle appeared, was selected, and rendered with SW+; both tracks remained listed.
- Paused position stayed at 22,875 ms for both additions.
- A third subtitle rendered with SW after returning from the picker to playing playback;
  the session reported PLAYING and its position advanced. All three tracks remained listed.
- Independent SW audio remained selected.
- No app crashes were recorded.

The JVM regression verifies duplicate subtitle IDs are a no-op. Separately, the emulator's
Downloads provider returned both `raw:` and `msf:` URIs for the same file on successive picks;
the existing URI-based deduplication treats these as different subtitles. This source-reload
fix does not change document identity handling.

Screenshots: [before](../../fastlane/metadata/qa/local-subtitle-refresh/before.png),
[after](../../fastlane/metadata/qa/local-subtitle-refresh/after.png), and
[rendering with SW](../../fastlane/metadata/qa/local-subtitle-refresh/rendering.png).
