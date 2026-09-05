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
